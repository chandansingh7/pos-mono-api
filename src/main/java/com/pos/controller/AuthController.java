package com.pos.controller;

import com.pos.dto.request.LoginRequest;
import com.pos.dto.request.RegisterRequest;
import com.pos.dto.response.ApiResponse;
import com.pos.dto.response.AuthResponse;
import com.pos.service.AccessLogService;
import com.pos.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AccessLogService accessLogService;

    @GetMapping("/client-ip")
    public ResponseEntity<ApiResponse<String>> getClientIp(HttpServletRequest httpRequest) {
        return ResponseEntity.ok(ApiResponse.ok(accessLogService.resolveClientIp(httpRequest)));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        String clientIp = accessLogService.resolveClientIp(httpRequest);
        return ResponseEntity.ok(ApiResponse.ok(authService.login(request, clientIp)));
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("User registered successfully", authService.register(request)));
    }
}
