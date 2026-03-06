package com.pos.service;

import com.pos.dto.response.AccessLogResponse;
import com.pos.dto.response.AccessLogSummaryResponse;
import com.pos.dto.response.UserIpUsageResponse;
import com.pos.entity.AccessLog;
import com.pos.repository.AccessLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
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

    /** Max recent log entries to consider when building summary (grouped by user + normalized IP). */
    private static final int SUMMARY_FETCH_CAP = 5000;

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
        String action = resolveAction(request.getMethod(), path);

        AccessLog logEntry = AccessLog.builder()
                .username(username)
                .ipAddress(ip)
                .country(country)
                .userAgent(userAgent)
                .path(path)
                .action(action)
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

    /**
     * Returns one row per (username, normalized IP) with request count and last activity.
     * Based on the most recent {@value #SUMMARY_FETCH_CAP} log entries to keep response size manageable.
     */
    @Transactional(readOnly = true)
    public Page<AccessLogSummaryResponse> listSummary(String username, int page, int size) {
        Pageable fetchAll = PageRequest.of(0, SUMMARY_FETCH_CAP);
        List<AccessLog> recent = (username == null || username.isBlank())
                ? accessLogRepository.findAllByOrderByCreatedAtDesc(fetchAll).getContent()
                : accessLogRepository.findByUsernameOrderByCreatedAtDesc(username, fetchAll).getContent();

        Map<String, List<AccessLog>> byKey = recent.stream()
                .filter(l -> l.getUsername() != null && l.getIpAddress() != null && !l.getIpAddress().isBlank())
                .collect(Collectors.groupingBy(AccessLogService::groupKey));

        List<AccessLogSummaryResponse> list = new ArrayList<>();
        for (List<AccessLog> entries : byKey.values()) {
            if (entries.isEmpty()) continue;
            AccessLog latest = entries.stream()
                    .max(Comparator.comparing(AccessLog::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                    .orElse(entries.get(0));
            String country = entries.stream()
                    .map(AccessLog::getCountry)
                    .filter(Objects::nonNull)
                    .filter(c -> !c.isBlank())
                    .findFirst()
                    .orElse(null);
            list.add(AccessLogSummaryResponse.builder()
                    .username(latest.getUsername())
                    .ipAddress(normalizeIp(latest.getIpAddress()))
                    .country(country)
                    .requestCount(entries.size())
                    .lastAction(latest.getAction())
                    .lastPath(latest.getPath())
                    .lastWhen(latest.getCreatedAt())
                    .build());
        }
        list.sort(Comparator.comparing(AccessLogSummaryResponse::getLastWhen, Comparator.nullsLast(Comparator.reverseOrder())));

        int total = list.size();
        int from = Math.min(page * size, total);
        int to = Math.min(from + size, total);
        List<AccessLogSummaryResponse> content = from < to ? list.subList(from, to) : Collections.emptyList();
        return new PageImpl<>(content, PageRequest.of(page, size), total);
    }

    private static String groupKey(AccessLog l) {
        String u = l.getUsername() != null ? l.getUsername() : "";
        String ip = l.getIpAddress() != null && !l.getIpAddress().isBlank()
                ? normalizeIp(l.getIpAddress()) : "";
        return u + "\0" + ip;
    }

    /**
     * Normalize IP by stripping port (e.g. "24.28.169.48:61639" -> "24.28.169.48")
     * so allow/block and reporting work by host, not per-connection.
     */
    public static String normalizeIp(String ip) {
        if (ip == null || ip.isBlank()) return ip;
        String s = ip.trim();
        int colon = s.lastIndexOf(':');
        if (colon > 0 && colon < s.length() - 1 && s.substring(colon + 1).matches("\\d+")) {
            return s.substring(0, colon);
        }
        return s;
    }

    @Transactional(readOnly = true)
    public List<UserIpUsageResponse> listIpsForUser(String username) {
        if (username == null || username.isBlank()) {
            return Collections.emptyList();
        }
        List<AccessLog> logs = accessLogRepository.findByUsername(username);
        // Group by normalized IP (host only) so we show one row per host, not per host:port
        Map<String, List<AccessLog>> byIp = logs.stream()
                .filter(l -> l.getIpAddress() != null && !l.getIpAddress().isBlank())
                .collect(Collectors.groupingBy(l -> normalizeIp(l.getIpAddress())));

        List<UserIpUsageResponse> result = new ArrayList<>();
        for (Map.Entry<String, List<AccessLog>> entry : byIp.entrySet()) {
            String normalizedIp = entry.getKey();
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
            result.add(new UserIpUsageResponse(normalizedIp, country, count, lastUsed));
        }

        result.sort(Comparator.comparing(UserIpUsageResponse::getLastUsedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return result;
    }

    /**
     * Resolve client IP from request (supports X-Forwarded-For and similar headers).
     * Public so auth and filters can use it for IP allow-list enforcement.
     */
    public String resolveClientIp(HttpServletRequest request) {
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

    private String resolveAction(String method, String path) {
        if (method == null || path == null) {
            return "";
        }
        method = method.toUpperCase(Locale.ROOT);

        // Orders
        if (method.equals("POST") && path.equals("/api/orders")) {
            return "Create order";
        }
        if (method.equals("PUT") && path.matches("^/api/orders/.+/cancel$")) {
            return "Cancel order";
        }

        // Products
        if (method.equals("POST") && path.equals("/api/products")) {
            return "Create product";
        }
        if (method.equals("PUT") && path.matches("^/api/products/\\d+$")) {
            return "Update product";
        }

        // Categories
        if (path.startsWith("/api/categories")) {
            if (method.equals("POST")) return "Create category";
            if (method.equals("PUT")) return "Update category";
            if (method.equals("DELETE")) return "Delete category";
        }

        // Customers
        if (path.startsWith("/api/customers")) {
            if (method.equals("POST")) return "Create/Update customer";
        }

        // Shifts
        if (method.equals("POST") && path.equals("/api/shifts/open")) {
            return "Open shift";
        }
        if (method.equals("POST") && path.equals("/api/shifts/close")) {
            return "Close shift";
        }

        // Auth
        if (method.equals("POST") && path.equals("/api/auth/login")) {
            return "Login";
        }
        if (method.equals("POST") && path.equals("/api/auth/register")) {
            return "Register user";
        }

        // Company / settings
        if (path.startsWith("/api/company")) {
            if (method.equals("PUT")) return "Update company settings";
            if (method.equals("POST") && path.contains("/logo")) return "Upload company logo";
            if (method.equals("POST") && path.contains("/favicon")) return "Upload company favicon";
        }

        // Default: fallback to METHOD path
        return method + " " + path;
    }
}

