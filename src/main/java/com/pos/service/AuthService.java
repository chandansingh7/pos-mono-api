package com.pos.service;

import com.pos.dto.request.ChangePasswordRequest;
import com.pos.dto.request.LoginRequest;
import com.pos.dto.request.RegisterRequest;
import com.pos.dto.response.AuthResponse;
import com.pos.entity.User;
import com.pos.enums.Role;
import com.pos.exception.BadRequestException;
import com.pos.exception.ErrorCode;
import com.pos.repository.UserRepository;
import com.pos.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository        userRepository;
    private final PasswordEncoder       passwordEncoder;
    private final JwtTokenProvider      jwtTokenProvider;
    private final AuthenticationManager authenticationManager;
    private final UserAllowedIpService userAllowedIpService;

    public AuthResponse login(LoginRequest request, String clientIp) {
        log.info("Login attempt for user: {}", request.getUsername());
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );
        User user = (User) auth.getPrincipal();
        if (!userAllowedIpService.isAllowed(user.getId(), clientIp)) {
            log.warn("[AU008] Login rejected — IP not allowed for user: {}, IP: {}", user.getUsername(), clientIp);
            throw new BadRequestException(ErrorCode.AU008);
        }
        String token = jwtTokenProvider.generateToken(user);
        log.info("Login successful — user: {}, role: {}", user.getUsername(), user.getRole());

        return AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole())
                .build();
    }

    public AuthResponse register(RegisterRequest request) {
        log.info("Registering new user: {}, role: {}", request.getUsername(), request.getRole());

        if (userRepository.existsByUsername(request.getUsername())) {
            log.warn("[US002] Registration failed — username taken: {}", request.getUsername());
            throw new BadRequestException(ErrorCode.US002);
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            log.warn("[US003] Registration failed — email registered: {}", request.getEmail());
            throw new BadRequestException(ErrorCode.US003);
        }

        Role role = request.getRole() != null ? request.getRole() : Role.CASHIER;
        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(role)
                .active(true)
                .build();

        userRepository.save(user);
        String token = jwtTokenProvider.generateToken(user);
        log.info("User registered — username: {}, role: {}", user.getUsername(), role);

        return AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole())
                .build();
    }

    public void changePassword(String username, ChangePasswordRequest request) {
        log.info("Password change requested by user: {}", username);
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BadRequestException(ErrorCode.US001));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            log.warn("[AU005] Password change failed — wrong current password: {}", username);
            throw new BadRequestException(ErrorCode.AU005);
        }
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            log.warn("[AU006] Password change failed — confirmation mismatch: {}", username);
            throw new BadRequestException(ErrorCode.AU006);
        }
        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            log.warn("[AU007] Password change failed — same as current: {}", username);
            throw new BadRequestException(ErrorCode.AU007);
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        log.info("Password changed successfully for user: {}", username);
    }
}
