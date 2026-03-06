package com.pos.backup;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductExport {
    private Long id;
    private String name;
    private String sku;
    private String barcode;
    private String size;
    private String color;
    private BigDecimal price;
    private String saleUnitType;
    private String saleUnit;
    private Long categoryId;
    private String imageUrl;
    private boolean active;
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime updatedAt;
}
