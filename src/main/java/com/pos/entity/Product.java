package com.pos.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "products")
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(unique = true)
    private String sku;

    @Column(unique = true)
    private String barcode;

    /** Optional size variant, e.g. S, M, L, 42. */
    private String size;

    /** Optional color variant, e.g. Red, Blue, Black. */
    private String color;

    /** Price per unit (unit given by saleUnit, e.g. per kg, per L, per each). */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    /**
     * How this product is sold: PIECE (each), WEIGHT (mass), VOLUME (liquid).
     * Determines which units are available in saleUnit.
     */
    @Column(name = "sale_unit_type", length = 10)
    private String saleUnitType;

    /**
     * Unit for price and quantity: each, kg, g, lb, oz, L, ml, gal, fl_oz.
     * Must match saleUnitType (piece→each; weight→kg/g/lb/oz; volume→L/ml/gal/fl_oz).
     */
    @Column(name = "sale_unit", length = 10)
    private String saleUnit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    private String imageUrl;

    @Builder.Default
    private boolean active = true;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private String updatedBy;

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
