package com.pos.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RefundRequest {
    @Size(max = 500, message = "Reason must be 500 characters or fewer")
    private String reason;
}
