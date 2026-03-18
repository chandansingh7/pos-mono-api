package com.pos.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Maps a named tax category (e.g. STANDARD, REDUCED, EXEMPT) to a rate.
 * Products reference a category key; OrderService resolves the actual rate at order time.
 */
@Entity
@Table(name = "tax_rules")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaxRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Unique category key stored on Product (e.g. STANDARD, REDUCED, EXEMPT, FOOD). */
    @Column(name = "tax_category", nullable = false, unique = true, length = 30)
    private String taxCategory;

    /** Human-readable label shown in UI (e.g. "Standard Rate", "Reduced Rate"). */
    @Column(nullable = false, length = 50)
    private String label;

    /** Rate as a decimal fraction: 0.08 = 8%, 0.00 = exempt. */
    @Column(nullable = false, precision = 6, scale = 4)
    private BigDecimal rate;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
