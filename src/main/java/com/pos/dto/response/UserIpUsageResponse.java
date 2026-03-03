package com.pos.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class UserIpUsageResponse {
    private String ipAddress;
    private String country;
    private long count;
    private LocalDateTime lastUsedAt;
}

