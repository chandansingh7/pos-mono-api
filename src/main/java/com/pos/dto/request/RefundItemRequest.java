package com.pos.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class RefundItemRequest {
    @NotNull(message = "Order item ID is required")
    private Long orderItemId;

    @NotNull(message = "Quantity is required")
    @DecimalMin(value = "0.0001", message = "Quantity must be positive")
    private BigDecimal quantity;
}
