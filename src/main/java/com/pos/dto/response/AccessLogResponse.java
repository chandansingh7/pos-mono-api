package com.pos.dto.response;

import com.pos.entity.AccessLog;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AccessLogResponse {

    private Long id;
    private String username;
    private String ipAddress;
    private String country;
    private String userAgent;
    private String path;
    private String action;
    private LocalDateTime createdAt;

    public static AccessLogResponse from(AccessLog log) {
        if (log == null) return null;
        return AccessLogResponse.builder()
                .id(log.getId())
                .username(log.getUsername())
                .ipAddress(log.getIpAddress())
                .country(log.getCountry())
                .userAgent(log.getUserAgent())
                .path(log.getPath())
                .action(log.getAction())
                .createdAt(log.getCreatedAt())
                .build();
    }
}

