package com.pos.controller;

import com.pos.dto.request.AddAllowedIpRequest;
import com.pos.dto.response.ApiResponse;
import com.pos.service.UserAllowedIpService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Allowed IPs", description = "Per-user IP allow list (whitelist) for login and API access")
@RestController
@RequestMapping("/api/users/allowed-ips")
@RequiredArgsConstructor
public class AllowedIpController {

    private final UserAllowedIpService userAllowedIpService;

    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    @Operation(summary = "List allowed IPs for a user (by username)")
    public ResponseEntity<ApiResponse<List<String>>> getAllowedIps(@RequestParam String username) {
        return ResponseEntity.ok(ApiResponse.ok(userAllowedIpService.getAllowedIpsByUsername(username)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    @Operation(summary = "Add an IP to a user's allow list")
    public ResponseEntity<ApiResponse<List<String>>> addAllowedIp(@Valid @RequestBody AddAllowedIpRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("IP added to allow list",
                userAllowedIpService.addAllowedIp(request.getUsername(), request.getIpAddress())));
    }

    @DeleteMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    @Operation(summary = "Remove an IP from a user's allow list")
    public ResponseEntity<ApiResponse<List<String>>> removeAllowedIp(
            @RequestParam String username,
            @RequestParam String ipAddress) {
        return ResponseEntity.ok(ApiResponse.ok("IP removed from allow list",
                userAllowedIpService.removeAllowedIp(username, ipAddress)));
    }
}
