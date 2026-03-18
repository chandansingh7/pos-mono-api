package com.pos.dto.response;

import com.pos.entity.OrderItem;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class OrderItemResponse {
    private Long id;
    private Long productId;
    private String productName;
    private String productSku;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private BigDecimal subtotal;
    /** Quantity already refunded (for partial refund display). */
    private BigDecimal refundedQuantity;

    public static OrderItemResponse from(OrderItem item) {
        return from(item, null);
    }

    public static OrderItemResponse from(OrderItem item, BigDecimal refundedQty) {
        return OrderItemResponse.builder()
                .id(item.getId())
                .productId(item.getProduct().getId())
                .productName(item.getProduct().getName())
                .productSku(item.getProduct().getSku())
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .subtotal(item.getSubtotal())
                .refundedQuantity(refundedQty != null ? refundedQty : BigDecimal.ZERO)
                .build();
    }
}
