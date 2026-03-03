package com.pos.dto.response;

import com.pos.entity.Shift;
import com.pos.enums.ShiftStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class ShiftResponse {

    private Long id;
    private String cashierUsername;
    private BigDecimal openingFloat;
    private BigDecimal cashSales;
    private BigDecimal expectedCash;
    private BigDecimal countedCash;
    private BigDecimal difference;
    private ShiftStatus status;
    private LocalDateTime openedAt;
    private LocalDateTime closedAt;

    public static ShiftResponse from(Shift shift) {
        if (shift == null) return null;
        return ShiftResponse.builder()
                .id(shift.getId())
                .cashierUsername(shift.getCashier() != null ? shift.getCashier().getUsername() : null)
                .openingFloat(shift.getOpeningFloat())
                .cashSales(shift.getCashSales())
                .expectedCash(shift.getExpectedCash())
                .countedCash(shift.getCountedCash())
                .difference(shift.getDifference())
                .status(shift.getStatus())
                .openedAt(shift.getOpenedAt())
                .closedAt(shift.getClosedAt())
                .build();
    }
}

