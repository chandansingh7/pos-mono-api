package com.pos.dto.response;

import com.pos.entity.Refund;
import com.pos.enums.PaymentMethod;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

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
    private List<RefundItemResponse> items;

    public static RefundResponse from(Refund r) {
        List<RefundItemResponse> items = r.getItems() != null
                ? r.getItems().stream().map(RefundItemResponse::from).collect(Collectors.toList())
                : Collections.emptyList();
        return RefundResponse.builder()
                .id(r.getId())
                .orderId(r.getOrder().getId())
                .refundedBy(r.getRefundedBy())
                .refundedAt(r.getRefundedAt())
                .amount(r.getAmount())
                .refundMethod(r.getRefundMethod())
                .reason(r.getReason())
                .rewardPointsDeducted(r.getRewardPointsDeducted())
                .items(items)
                .build();
    }
}
