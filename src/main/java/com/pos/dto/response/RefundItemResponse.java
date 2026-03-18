package com.pos.dto.response;

import com.pos.entity.RefundItem;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class RefundItemResponse {
    private Long orderItemId;
    private String productName;
    private BigDecimal quantity;
    private BigDecimal amount;

    public static RefundItemResponse from(RefundItem ri) {
        return RefundItemResponse.builder()
                .orderItemId(ri.getOrderItem().getId())
                .productName(ri.getOrderItem().getProduct().getName())
                .quantity(ri.getQuantity())
                .amount(ri.getAmount())
                .build();
    }
}
