package com.pos.dto.response;

import com.pos.entity.Inventory;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class InventoryResponse {
    private Long id;
    private Long productId;
    private String productName;
    private String productSku;
    private BigDecimal quantity;
    private int lowStockThreshold;
    private String stockStatus;
    private LocalDateTime updatedAt;
    private String updatedBy;

    public static InventoryResponse from(Inventory inv) {
        String status;
        if (inv.getQuantity().compareTo(BigDecimal.ZERO) == 0) {
            status = "OUT_OF_STOCK";
        } else if (inv.getQuantity().compareTo(BigDecimal.valueOf(inv.getLowStockThreshold())) <= 0) {
            status = "LOW_STOCK";
        } else {
            status = "IN_STOCK";
        }

        return InventoryResponse.builder()
                .id(inv.getId())
                .productId(inv.getProduct().getId())
                .productName(inv.getProduct().getName())
                .productSku(inv.getProduct().getSku())
                .quantity(inv.getQuantity())
                .lowStockThreshold(inv.getLowStockThreshold())
                .stockStatus(status)
                .updatedAt(inv.getUpdatedAt())
                .updatedBy(inv.getUpdatedBy())
                .build();
    }
}
