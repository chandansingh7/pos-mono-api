package com.pos.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OfflineOrderSyncResult {

    private String localId;
    private Long serverOrderId;
    private String status; // "ok" | "rejected"
    private String reason; // when rejected
}
