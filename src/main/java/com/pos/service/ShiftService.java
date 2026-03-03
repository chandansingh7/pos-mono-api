package com.pos.service;

import com.pos.dto.request.CloseShiftRequest;
import com.pos.dto.request.OpenShiftRequest;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShiftService {

    private final ShiftRepository shiftRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;

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

    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            throw new BadRequestException(ErrorCode.AU004, "No authenticated user");
        }
        return userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.US001));
    }
}

