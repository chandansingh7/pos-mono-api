package com.pos.dto.response;

import com.pos.entity.Product;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class ProductResponse {
    private Long id;
    private String name;
    private String sku;
    private String barcode;
    private String size;
    private String color;
    private BigDecimal price;
    private Long categoryId;
    private String categoryName;
    private String imageUrl;
    private boolean active;
    private BigDecimal quantity;
    private String saleUnitType;
    private String saleUnit;
    /** Tax category key (e.g. STANDARD, REDUCED, EXEMPT). Null → global company rate. */
    private String taxCategory;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String updatedBy;

    public static ProductResponse from(Product p, BigDecimal quantity) {
        return ProductResponse.builder()
                .id(p.getId())
                .name(p.getName())
                .sku(p.getSku())
                .barcode(p.getBarcode())
                .size(p.getSize())
                .color(p.getColor())
                .price(p.getPrice())
                .categoryId(p.getCategory() != null ? p.getCategory().getId() : null)
                .categoryName(p.getCategory() != null ? p.getCategory().getName() : null)
                .imageUrl(p.getImageUrl())
                .active(p.isActive())
                .quantity(quantity)
                .saleUnitType(p.getSaleUnitType())
                .saleUnit(p.getSaleUnit())
                .taxCategory(p.getTaxCategory())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .updatedBy(p.getUpdatedBy())
                .build();
    }
}
