package com.pos.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class RefundRequest {
    @Size(max = 500, message = "Reason must be 500 characters or fewer")
    private String reason;

    /**
     * When provided, only these items/quantities are refunded (partial refund).
     * When null or empty, full order refund is performed.
     */
    @Valid
    private List<RefundItemRequest> items;
}
