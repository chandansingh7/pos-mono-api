package com.pos.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * One row per (username, normalized IP) for the access log summary view.
 * Reduces many rows (same IP, different ports) to a single row per host.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccessLogSummaryResponse {

    private String username;
    private String ipAddress;  // normalized (host only, no port)
    private String country;
    private long requestCount;
    private String lastAction;
    private String lastPath;
    private LocalDateTime lastWhen;
}
