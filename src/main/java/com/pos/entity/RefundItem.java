package com.pos.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Tracks which order items (and quantities) were included in a partial refund.
 */
@Entity
@Table(name = "refund_items")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "refund_id", nullable = false)
    private Refund refund;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_item_id", nullable = false)
    private OrderItem orderItem;

    @Column(nullable = false, precision = 12, scale = 4)
    private BigDecimal quantity;

    /** Subtotal for this refunded portion (quantity * unitPrice). */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;
}
