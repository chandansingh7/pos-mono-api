package com.pos.service;

import com.pos.dto.response.AccessLogResponse;
import com.pos.dto.response.UserIpUsageResponse;
import com.pos.entity.AccessLog;
import com.pos.repository.AccessLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccessLogService {

    private final AccessLogRepository accessLogRepository;

    /**
     * Persist a single access log entry for the current authenticated user.
     * Called from filters after security has been applied.
     */
    @Transactional
    public void log(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return;
        }

        String username = auth.getName();
        if (username == null || username.isBlank()) {
            return;
        }

        String path = request.getRequestURI();
        // Only track API usage, not static assets
        if (path == null || !path.startsWith("/api/")) {
            return;
        }

        String ip = resolveClientIp(request);
        String country = resolveCountry(request);
        String userAgent = Optional.ofNullable(request.getHeader("User-Agent")).orElse("");

        AccessLog logEntry = AccessLog.builder()
                .username(username)
                .ipAddress(ip)
                .country(country)
                .userAgent(userAgent)
                .path(path)
                .build();

        accessLogRepository.save(logEntry);
    }

    @Transactional(readOnly = true)
    public Page<AccessLogResponse> list(String username, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<AccessLog> logs = (username == null || username.isBlank())
                ? accessLogRepository.findAllByOrderByCreatedAtDesc(pageable)
                : accessLogRepository.findByUsernameOrderByCreatedAtDesc(username, pageable);
        return logs.map(AccessLogResponse::from);
    }

    @Transactional(readOnly = true)
    public List<UserIpUsageResponse> listIpsForUser(String username) {
        if (username == null || username.isBlank()) {
            return Collections.emptyList();
        }
        List<AccessLog> logs = accessLogRepository.findByUsername(username);
        Map<String, List<AccessLog>> byIp = logs.stream()
                .filter(l -> l.getIpAddress() != null && !l.getIpAddress().isBlank())
                .collect(Collectors.groupingBy(AccessLog::getIpAddress));

        List<UserIpUsageResponse> result = new ArrayList<>();
        for (Map.Entry<String, List<AccessLog>> entry : byIp.entrySet()) {
            String ip = entry.getKey();
            List<AccessLog> entries = entry.getValue();
            long count = entries.size();
            LocalDateTime lastUsed = entries.stream()
                    .map(AccessLog::getCreatedAt)
                    .filter(Objects::nonNull)
                    .max(LocalDateTime::compareTo)
                    .orElse(null);
            String country = entries.stream()
                    .map(AccessLog::getCountry)
                    .filter(Objects::nonNull)
                    .filter(c -> !c.isBlank())
                    .findFirst()
                    .orElse(null);
            result.add(new UserIpUsageResponse(ip, country, count, lastUsed));
        }

        result.sort(Comparator.comparing(UserIpUsageResponse::getLastUsedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return result;
    }

    private String resolveClientIp(HttpServletRequest request) {
        String header = request.getHeader("X-Forwarded-For");
        if (header != null && !header.isBlank()) {
            // In case of multiple proxies, first IP is the original client
            return header.split(",")[0].trim();
        }
        String ip = request.getRemoteAddr();
        return ip != null ? ip : "";
    }

    /**
     * Best-effort country resolution from common proxy headers.
     * If no header is present, returns null.
     */
    private String resolveCountry(HttpServletRequest request) {
        String cf = request.getHeader("CF-IPCountry");
        if (cf != null && !cf.isBlank()) return cf;

        String azure = request.getHeader("X-Azure-ClientCountry");
        if (azure != null && !azure.isBlank()) return azure;

        String appEngine = request.getHeader("X-AppEngine-Country");
        if (appEngine != null && !appEngine.isBlank() && !"ZZ".equalsIgnoreCase(appEngine)) return appEngine;

        return null;
    }
}

