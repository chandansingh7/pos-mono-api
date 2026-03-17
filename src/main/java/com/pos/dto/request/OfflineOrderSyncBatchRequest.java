package com.pos.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class OfflineOrderSyncBatchRequest {

    @NotEmpty(message = "At least one order is required")
    @Valid
    private List<OfflineOrderSyncRequest> orders;
}
