package com.pos.controller;

import com.pos.dto.response.AccessLogResponse;
import com.pos.dto.response.AccessLogSummaryResponse;
import com.pos.dto.response.ApiResponse;
import com.pos.dto.response.UserIpUsageResponse;
import com.pos.service.AccessLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/access-logs")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ADMIN')")
public class AccessLogController {

    private final AccessLogService accessLogService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<AccessLogResponse>>> getLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String username) {
        Page<AccessLogResponse> logs = accessLogService.list(username, page, size);
        return ResponseEntity.ok(ApiResponse.ok(logs));
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<Page<AccessLogSummaryResponse>>> getSummary(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String username) {
        Page<AccessLogSummaryResponse> summary = accessLogService.listSummary(username, page, size);
        return ResponseEntity.ok(ApiResponse.ok(summary));
    }

    @GetMapping("/ips")
    public ResponseEntity<ApiResponse<List<UserIpUsageResponse>>> getUserIps(
            @RequestParam String username) {
        List<UserIpUsageResponse> ips = accessLogService.listIpsForUser(username);
        return ResponseEntity.ok(ApiResponse.ok(ips));
    }
}

