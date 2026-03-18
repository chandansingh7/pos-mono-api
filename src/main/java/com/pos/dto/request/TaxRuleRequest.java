package com.pos.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class TaxRuleRequest {

    @NotBlank(message = "Tax category key is required")
    @Size(max = 30, message = "Tax category must be 30 characters or fewer")
    @Pattern(regexp = "^[A-Z0-9_]+$", message = "Tax category must be uppercase letters, digits or underscores")
    private String taxCategory;

    @NotBlank(message = "Label is required")
    @Size(max = 50, message = "Label must be 50 characters or fewer")
    private String label;

    @NotNull(message = "Rate is required")
    @DecimalMin(value = "0.0000", message = "Rate cannot be negative")
    @DecimalMax(value = "1.0000", message = "Rate cannot exceed 100%")
    private BigDecimal rate;
}
