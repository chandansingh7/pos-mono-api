package com.pos.entity;

import com.pos.enums.PaymentMethod;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Immutable audit record created when an order (or part of it) is refunded.
 * Never updated or deleted — provides a permanent trail of all refund events.
 * For partial refunds, items list tracks which order items were refunded.
 */
@Entity
@Table(name = "refunds")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Refund {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    /** Username of the staff member who issued the refund. */
    @Column(name = "refunded_by", nullable = false, length = 100)
    private String refundedBy;

    @Column(name = "refunded_at", nullable = false)
    private LocalDateTime refundedAt;

    /** Amount refunded (full or partial). */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    /** Payment method from the original order (for cashier reconciliation). */
    @Enumerated(EnumType.STRING)
    @Column(name = "refund_method", length = 20)
    private PaymentMethod refundMethod;

    /** Optional reason provided by staff at time of refund. */
    @Column(length = 500)
    private String reason;

    /** Reward points that were deducted from the customer as part of this refund. */
    @Column(name = "reward_points_deducted")
    private Integer rewardPointsDeducted;

    @OneToMany(mappedBy = "refund", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<RefundItem> items = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (refundedAt == null) {
            refundedAt = LocalDateTime.now();
        }
    }
}
