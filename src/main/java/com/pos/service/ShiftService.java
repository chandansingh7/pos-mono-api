package com.pos.service;

import com.pos.config.ShiftConfig;
import com.pos.dto.request.CloseShiftRequest;
import com.pos.dto.request.OpenShiftRequest;
import com.pos.dto.response.ShiftListResponse;
import com.pos.dto.response.ShiftResponse;
import com.pos.entity.Shift;
import com.pos.entity.User;
import com.pos.enums.PaymentMethod;
import com.pos.enums.PaymentStatus;
import com.pos.enums.ShiftStatus;
import com.pos.exception.BadRequestException;
import com.pos.exception.ErrorCode;
import com.pos.exception.ResourceNotFoundException;
import com.pos.repository.PaymentRepository;
import com.pos.repository.ShiftRepository;
import com.pos.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShiftService {

    private final ShiftRepository shiftRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final ShiftConfig shiftConfig;

    @Transactional
    public ShiftResponse open(OpenShiftRequest request) {
        User cashier = currentUser();
        shiftRepository.findByCashierAndStatus(cashier, ShiftStatus.OPEN).ifPresent(s -> {
            throw new BadRequestException(ErrorCode.OR003, "Shift already open for this cashier");
        });

        Shift shift = Shift.builder()
                .cashier(cashier)
                .openingFloat(request.getOpeningFloat())
                .cashSales(BigDecimal.ZERO)
                .expectedCash(request.getOpeningFloat())
                .status(ShiftStatus.OPEN)
                .openedAt(LocalDateTime.now())
                .build();
        shift = shiftRepository.save(shift);

        log.info("Shift opened — id: {}, cashier: {}", shift.getId(), cashier.getEmail());
        return ShiftResponse.from(shift);
    }

    @Transactional(readOnly = true)
    public ShiftResponse getCurrent() {
        User cashier = currentUser();
        Shift shift = shiftRepository.findByCashierAndStatus(cashier, ShiftStatus.OPEN)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.OR001, "No open shift"));

        BigDecimal cashSales = paymentRepository.sumByMethodAndStatusAndCashierAndCreatedAtBetween(
                PaymentMethod.CASH, PaymentStatus.COMPLETED,
                cashier, shift.getOpenedAt(), LocalDateTime.now());
        BigDecimal expected = shift.getOpeningFloat().add(cashSales);

        ShiftResponse resp = ShiftResponse.from(shift);
        resp.setCashSales(cashSales);
        resp.setExpectedCash(expected);
        return resp;
    }

    @Transactional
    public ShiftResponse close(CloseShiftRequest request) {
        User cashier = currentUser();
        Shift shift = shiftRepository.findByCashierAndStatus(cashier, ShiftStatus.OPEN)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.OR001, "No open shift"));

        LocalDateTime now = LocalDateTime.now();
        BigDecimal cashSales = paymentRepository.sumByMethodAndStatusAndCashierAndCreatedAtBetween(
                PaymentMethod.CASH, PaymentStatus.COMPLETED,
                cashier, shift.getOpenedAt(), now);
        BigDecimal expected = shift.getOpeningFloat().add(cashSales);
        BigDecimal counted = request.getCountedCash();
        BigDecimal difference = counted.subtract(expected);

        // ── Configurable business rules before closing (for cashier) ──────────
        BigDecimal maxDiff = shiftConfig.getMaxDifferenceAbsolute();
        if (maxDiff != null && maxDiff.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal abs = difference.abs();
            if (abs.compareTo(maxDiff) > 0) {
                log.warn("[SH001] Shift close rejected — diff: {}, max allowed: {}", abs, maxDiff);
                throw new BadRequestException(ErrorCode.SH001,
                        "Cash drawer difference " + abs + " exceeds allowed tolerance " + maxDiff);
            }
        }

        long minMinutes = shiftConfig.getMinOpenMinutes();
        if (minMinutes > 0 && shift.getOpenedAt() != null) {
            long openMinutes = Duration.between(shift.getOpenedAt(), now).toMinutes();
            if (openMinutes < minMinutes) {
                log.warn("[SH002] Shift close rejected — open for {} min, minimum required: {}", openMinutes, minMinutes);
                throw new BadRequestException(ErrorCode.SH002,
                        "Shift has been open for only " + openMinutes + " minutes (minimum " + minMinutes + ").");
            }
        }

        long maxHours = shiftConfig.getMaxOpenHours();
        if (maxHours > 0 && shift.getOpenedAt() != null) {
            long openHours = Duration.between(shift.getOpenedAt(), now).toHours();
            if (openHours > maxHours) {
                log.warn("[SH003] Shift close rejected — open for {} h, maximum allowed: {}", openHours, maxHours);
                throw new BadRequestException(ErrorCode.SH003,
                        "Shift has been open for " + openHours + " hours (maximum " + maxHours + "). Ask an administrator to review.");
            }
        }

        if (shiftConfig.isRequireSameDay() && shift.getOpenedAt() != null
                && !shift.getOpenedAt().toLocalDate().equals(now.toLocalDate())) {
            log.warn("[SH004] Shift close rejected — opened at {}, now {}", shift.getOpenedAt(), now);
            throw new BadRequestException(ErrorCode.SH004);
        }

        shift.setCashSales(cashSales);
        shift.setExpectedCash(expected);
        shift.setCountedCash(counted);
        shift.setDifference(difference);
        shift.setStatus(ShiftStatus.CLOSED);
        shift.setClosedAt(now);

        shift = shiftRepository.save(shift);
        log.info("Shift closed — id: {}, cashier: {}, expected: {}, counted: {}, diff: {}",
                shift.getId(), cashier.getEmail(), expected, counted, difference);
        return ShiftResponse.from(shift);
    }

    /**
     * Administrative force-close that can be used to resolve stale shifts
     * (e.g. left open overnight). This bypasses the duration / same-day
     * constraints, but still computes cash sales, expected cash and the
     * over/short difference using the provided countedCash value.
     */
    @Transactional
    public ShiftResponse forceClose(Long shiftId, CloseShiftRequest request) {
        Shift shift = shiftRepository.findById(shiftId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.OR001, "Shift not found"));
        if (shift.getStatus() != ShiftStatus.OPEN) {
            throw new BadRequestException(ErrorCode.OR003, "Shift is not open");
        }

        User cashier = shift.getCashier();
        LocalDateTime now = LocalDateTime.now();

        BigDecimal cashSales = paymentRepository.sumByMethodAndStatusAndCashierAndCreatedAtBetween(
                PaymentMethod.CASH, PaymentStatus.COMPLETED,
                cashier, shift.getOpenedAt(), now);
        BigDecimal expected = shift.getOpeningFloat().add(cashSales);
        BigDecimal counted = request.getCountedCash();
        BigDecimal difference = counted.subtract(expected);

        shift.setCashSales(cashSales);
        shift.setExpectedCash(expected);
        shift.setCountedCash(counted);
        shift.setDifference(difference);
        shift.setStatus(ShiftStatus.CLOSED);
        shift.setClosedAt(now);

        shift = shiftRepository.save(shift);
        log.info("Shift force-closed — id: {}, cashier: {}, expected: {}, counted: {}, diff: {}",
                shift.getId(), cashier != null ? cashier.getEmail() : "n/a", expected, counted, difference);
        return ShiftResponse.from(shift);
    }

    @Transactional(readOnly = true)
    public ShiftListResponse listForAdmin(int page, int size) {
        long openCount = shiftRepository.countByStatus(ShiftStatus.OPEN);
        Pageable pageable = PageRequest.of(page, size);
        List<Shift> list = shiftRepository.findAllByOrderByOpenedAtDesc(pageable);
        List<ShiftResponse> responses = list.stream()
                .map(ShiftResponse::from)
                .collect(Collectors.toList());
        return ShiftListResponse.builder()
                .openCount(openCount)
                .shifts(responses)
                .build();
    }

    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            throw new BadRequestException(ErrorCode.AU004, "No authenticated user");
        }
        if (auth.getPrincipal() instanceof User user) {
            return user;
        }
        String username = auth.getName();
        if (username == null) {
            throw new BadRequestException(ErrorCode.AU004, "No authenticated user");
        }
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.US001));
    }
}

