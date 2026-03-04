package com.pos.controller;

import com.pos.dto.request.AddAllowedIpRequest;
import com.pos.dto.response.ApiResponse;
import com.pos.service.UserBlockedIpService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Blocked IPs", description = "Per-user IP block list (blacklist)")
@RestController
@RequestMapping("/api/users/blocked-ips")
@RequiredArgsConstructor
public class BlockedIpController {

    private final UserBlockedIpService userBlockedIpService;

    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    @Operation(summary = "List blocked IPs for a user (by username)")
    public ResponseEntity<ApiResponse<List<String>>> getBlockedIps(@RequestParam String username) {
        return ResponseEntity.ok(ApiResponse.ok(userBlockedIpService.getBlockedIpsByUsername(username)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    @Operation(summary = "Block an IP for a user")
    public ResponseEntity<ApiResponse<List<String>>> addBlockedIp(@Valid @RequestBody AddAllowedIpRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("IP added to block list",
                userBlockedIpService.addBlockedIp(request.getUsername(), request.getIpAddress())));
    }

    @DeleteMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    @Operation(summary = "Unblock (whitelist) an IP for a user")
    public ResponseEntity<ApiResponse<List<String>>> removeBlockedIp(
            @RequestParam String username,
            @RequestParam String ipAddress) {
        return ResponseEntity.ok(ApiResponse.ok("IP removed from block list",
                userBlockedIpService.removeBlockedIp(username, ipAddress)));
    }
}
