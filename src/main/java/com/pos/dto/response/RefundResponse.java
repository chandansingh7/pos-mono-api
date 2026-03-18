package com.pos.dto.response;

import com.pos.entity.Refund;
import com.pos.enums.PaymentMethod;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class RefundResponse {
    private Long id;
    private Long orderId;
    private String refundedBy;
    private LocalDateTime refundedAt;
    private BigDecimal amount;
    private PaymentMethod refundMethod;
    private String reason;
    private Integer rewardPointsDeducted;

    public static RefundResponse from(Refund r) {
        return RefundResponse.builder()
                .id(r.getId())
                .orderId(r.getOrder().getId())
                .refundedBy(r.getRefundedBy())
                .refundedAt(r.getRefundedAt())
                .amount(r.getAmount())
                .refundMethod(r.getRefundMethod())
                .reason(r.getReason())
                .rewardPointsDeducted(r.getRewardPointsDeducted())
                .build();
    }
}
