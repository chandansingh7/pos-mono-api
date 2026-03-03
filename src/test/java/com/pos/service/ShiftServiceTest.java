package com.pos.service;

import com.pos.config.ShiftConfig;
import com.pos.dto.request.CloseShiftRequest;
import com.pos.dto.request.OpenShiftRequest;
import com.pos.dto.response.ShiftResponse;
import com.pos.entity.Shift;
import com.pos.entity.User;
import com.pos.enums.PaymentMethod;
import com.pos.enums.Role;
import com.pos.enums.PaymentStatus;
import com.pos.enums.ShiftStatus;
import com.pos.exception.BadRequestException;
import com.pos.repository.PaymentRepository;
import com.pos.repository.ShiftRepository;
import com.pos.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ShiftServiceTest {

    @Mock
    private ShiftRepository shiftRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ShiftConfig shiftConfig;

    @InjectMocks
    private ShiftService shiftService;

    private User cashier;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        cashier = new User();
        cashier.setId(1L);
        cashier.setUsername("cashier1");
        cashier.setEmail("cashier@example.com");
        cashier.setRole(Role.CASHIER);
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(cashier, "pw", cashier.getAuthorities())
        );

        when(shiftConfig.getMaxDifferenceAbsolute()).thenReturn(BigDecimal.ZERO);
        when(shiftConfig.getMinOpenMinutes()).thenReturn(0L);
    }

    @Test
    void open_shouldCreateShiftWhenNoneOpen() {
        when(shiftRepository.findByCashierAndStatus(any(), eq(ShiftStatus.OPEN)))
                .thenReturn(Optional.empty());
        when(shiftRepository.save(any(Shift.class))).thenAnswer(invocation -> {
            Shift s = invocation.getArgument(0);
            s.setId(10L);
            return s;
        });

        OpenShiftRequest req = new OpenShiftRequest();
        req.setOpeningFloat(new BigDecimal("100.00"));

        ShiftResponse resp = shiftService.open(req);

        assertNotNull(resp.getId());
        assertEquals(new BigDecimal("100.00"), resp.getOpeningFloat());
        verify(shiftRepository, times(1)).save(any(Shift.class));
    }

    @Test
    void open_shouldFailWhenShiftAlreadyOpen() {
        when(shiftRepository.findByCashierAndStatus(any(), eq(ShiftStatus.OPEN)))
                .thenReturn(Optional.of(new Shift()));

        OpenShiftRequest req = new OpenShiftRequest();
        req.setOpeningFloat(new BigDecimal("50.00"));

        assertThrows(BadRequestException.class, () -> shiftService.open(req));
        verify(shiftRepository, never()).save(any());
    }

    @Test
    void close_shouldComputeExpectedAndDifference() {
        Shift shift = new Shift();
        shift.setId(20L);
        shift.setCashier(cashier);
        shift.setOpeningFloat(new BigDecimal("100.00"));
        shift.setOpenedAt(LocalDateTime.now().minusHours(2));
        shift.setStatus(ShiftStatus.OPEN);

        when(shiftRepository.findByCashierAndStatus(any(), eq(ShiftStatus.OPEN)))
                .thenReturn(Optional.of(shift));
        when(paymentRepository.sumByMethodAndStatusAndCashierAndCreatedAtBetween(
                eq(PaymentMethod.CASH), eq(PaymentStatus.COMPLETED), eq(cashier), any(), any()))
                .thenReturn(new BigDecimal("250.00"));
        when(shiftRepository.save(any(Shift.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CloseShiftRequest req = new CloseShiftRequest();
        req.setCountedCash(new BigDecimal("360.00"));

        ShiftResponse resp = shiftService.close(req);

        assertEquals(new BigDecimal("100.00"), resp.getOpeningFloat());
        assertEquals(new BigDecimal("250.00"), resp.getCashSales());
        assertEquals(new BigDecimal("350.00"), resp.getExpectedCash());
        assertEquals(new BigDecimal("10.00"), resp.getDifference());
        assertEquals(ShiftStatus.CLOSED, resp.getStatus());
    }

    @Test
    void close_shouldRejectWhenDifferenceExceedsTolerance() {
        Shift shift = new Shift();
        shift.setId(21L);
        shift.setCashier(cashier);
        shift.setOpeningFloat(new BigDecimal("100.00"));
        shift.setOpenedAt(LocalDateTime.now().minusHours(2));
        shift.setStatus(ShiftStatus.OPEN);

        when(shiftRepository.findByCashierAndStatus(any(), eq(ShiftStatus.OPEN)))
                .thenReturn(Optional.of(shift));
        when(paymentRepository.sumByMethodAndStatusAndCashierAndCreatedAtBetween(
                eq(PaymentMethod.CASH), eq(PaymentStatus.COMPLETED), eq(cashier), any(), any()))
                .thenReturn(new BigDecimal("250.00"));
        when(shiftConfig.getMaxDifferenceAbsolute()).thenReturn(new BigDecimal("5.00"));

        CloseShiftRequest req = new CloseShiftRequest();
        // expected = 350 → diff = 20 > 5 tolerance
        req.setCountedCash(new BigDecimal("370.00"));

        assertThrows(BadRequestException.class, () -> shiftService.close(req));
        verify(shiftRepository, never()).save(any(Shift.class));
    }

    @Test
    void close_shouldRejectWhenOpenTimeBelowMinimum() {
        Shift shift = new Shift();
        shift.setId(22L);
        shift.setCashier(cashier);
        shift.setOpeningFloat(new BigDecimal("100.00"));
        // Opened 5 minutes ago
        shift.setOpenedAt(LocalDateTime.now().minusMinutes(5));
        shift.setStatus(ShiftStatus.OPEN);

        when(shiftRepository.findByCashierAndStatus(any(), eq(ShiftStatus.OPEN)))
                .thenReturn(Optional.of(shift));
        when(paymentRepository.sumByMethodAndStatusAndCashierAndCreatedAtBetween(
                eq(PaymentMethod.CASH), eq(PaymentStatus.COMPLETED), eq(cashier), any(), any()))
                .thenReturn(new BigDecimal("0.00"));
        when(shiftConfig.getMinOpenMinutes()).thenReturn(30L);

        CloseShiftRequest req = new CloseShiftRequest();
        req.setCountedCash(new BigDecimal("100.00"));

        assertThrows(BadRequestException.class, () -> shiftService.close(req));
        verify(shiftRepository, never()).save(any(Shift.class));
    }
}

