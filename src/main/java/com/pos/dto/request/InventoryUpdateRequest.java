package com.pos.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class InventoryUpdateRequest {
    @DecimalMin(value = "0", message = "Quantity cannot be negative")
    private BigDecimal quantity;

    @Min(value = 0, message = "Low stock threshold cannot be negative")
    private int lowStockThreshold = 10;
}
