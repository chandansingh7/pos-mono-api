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
import java.util.Map;
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
    /** Total amount refunded so far (for partial refunds). */
    private BigDecimal refundedAmount;
    /** All refund records for this order (partial + full). */
    private List<RefundResponse> refunds;

    public static OrderResponse from(Order order) {
        return from(order, null, null, null);
    }

    public static OrderResponse from(Order order, List<Refund> refunds, BigDecimal refundedAmount,
                                    Map<Long, BigDecimal> refundedQtyByOrderItemId) {
        List<OrderItemResponse> itemResponses = order.getItems().stream()
                .map(item -> OrderItemResponse.from(item,
                        refundedQtyByOrderItemId != null ? refundedQtyByOrderItemId.get(item.getId()) : null))
                .collect(Collectors.toList());
        List<RefundResponse> refundResponses = refunds != null
                ? refunds.stream().map(RefundResponse::from).collect(Collectors.toList())
                : null;
        return OrderResponse.builder()
                .id(order.getId())
                .customerId(order.getCustomer() != null ? order.getCustomer().getId() : null)
                .customerName(order.getCustomer() != null ? order.getCustomer().getName() : "Walk-in")
                .customerEmail(order.getCustomer() != null ? order.getCustomer().getEmail() : null)
                .cashierUsername(order.getCashier().getUsername())
                .items(itemResponses)
                .subtotal(order.getSubtotal())
                .tax(order.getTax())
                .discount(order.getDiscount())
                .total(order.getTotal())
                .status(order.getStatus())
                .paymentMethod(order.getPaymentMethod())
                .createdAt(order.getCreatedAt())
                .refundedAmount(refundedAmount != null ? refundedAmount : BigDecimal.ZERO)
                .refunds(refundResponses)
                .build();
    }
}
