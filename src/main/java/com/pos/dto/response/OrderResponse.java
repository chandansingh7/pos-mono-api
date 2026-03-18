package com.pos.dto.response;

import com.pos.entity.Order;
import com.pos.entity.Refund;
import com.pos.enums.OrderStatus;
import com.pos.enums.PaymentMethod;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Data
@Builder
public class OrderResponse {
    private Long id;
    private Long customerId;
    private String customerName;
    private String customerEmail;
    private String cashierUsername;
    private List<OrderItemResponse> items;
    private BigDecimal subtotal;
    private BigDecimal tax;
    private BigDecimal discount;
    private BigDecimal total;
    private OrderStatus status;
    private PaymentMethod paymentMethod;
    private LocalDateTime createdAt;
    /** Populated only when status = REFUNDED. */
    private RefundResponse refund;

    public static OrderResponse from(Order order) {
        return from(order, null);
    }

    public static OrderResponse from(Order order, Refund refund) {
        return OrderResponse.builder()
                .id(order.getId())
                .customerId(order.getCustomer() != null ? order.getCustomer().getId() : null)
                .customerName(order.getCustomer() != null ? order.getCustomer().getName() : "Walk-in")
                .customerEmail(order.getCustomer() != null ? order.getCustomer().getEmail() : null)
                .cashierUsername(order.getCashier().getUsername())
                .items(order.getItems().stream().map(OrderItemResponse::from).collect(Collectors.toList()))
                .subtotal(order.getSubtotal())
                .tax(order.getTax())
                .discount(order.getDiscount())
                .total(order.getTotal())
                .status(order.getStatus())
                .paymentMethod(order.getPaymentMethod())
                .createdAt(order.getCreatedAt())
                .refund(refund != null ? RefundResponse.from(refund) : null)
                .build();
    }
}
