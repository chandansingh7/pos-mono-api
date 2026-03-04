package com.pos.service;

import com.pos.entity.User;
import com.pos.entity.UserAllowedIp;
import com.pos.exception.BadRequestException;
import com.pos.exception.ErrorCode;
import com.pos.exception.ResourceNotFoundException;
import com.pos.entity.UserBlockedIp;
import com.pos.repository.UserAllowedIpRepository;
import com.pos.repository.UserBlockedIpRepository;
import com.pos.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserAllowedIpService {

    private final UserRepository userRepository;
    private final UserAllowedIpRepository userAllowedIpRepository;
    private final UserBlockedIpRepository userBlockedIpRepository;

    /**
     * Normalize IP by stripping optional port (e.g. "24.28.169.48:57706" -> "24.28.169.48")
     * so the same host is allowed regardless of port.
     */
    private static String normalizeIp(String ip) {
        if (ip == null || ip.isBlank()) return ip;
        String s = ip.trim();
        int lastColon = s.lastIndexOf(':');
        if (lastColon > 0 && lastColon < s.length() - 1) {
            String after = s.substring(lastColon + 1);
            if (after.matches("\\d+")) return s.substring(0, lastColon);
        }
        return s;
    }

    /**
     * Returns true if this user is allowed to access from the given IP.
     * Block list is checked first (blocked IPs always denied). Then allow list:
     * if the user has no allowed IPs configured, all non-blocked IPs are allowed.
     * Comparison uses host-only (port stripped).
     */
    @Transactional(readOnly = true)
    public boolean isAllowed(Long userId, String clientIp) {
        if (userId == null || clientIp == null || clientIp.isBlank()) {
            return true;
        }
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return true;
        String normalized = normalizeIp(clientIp);
        List<UserBlockedIp> blocked = userBlockedIpRepository.findByUserOrderByCreatedAtDesc(user);
        if (blocked.stream().anyMatch(b -> normalized.equals(normalizeIp(b.getIpAddress())))) {
            return false;
        }
        List<UserAllowedIp> allowed = userAllowedIpRepository.findByUserOrderByCreatedAtDesc(user);
        if (allowed.isEmpty()) {
            return true;
        }
        return allowed.stream().anyMatch(a -> normalized.equals(normalizeIp(a.getIpAddress())));
    }

    @Transactional(readOnly = true)
    public List<String> getAllowedIpsByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.US001));
        return userAllowedIpRepository.findByUserOrderByCreatedAtDesc(user).stream()
                .map(UserAllowedIp::getIpAddress)
                .collect(Collectors.toList());
    }

    @Transactional
    public List<String> addAllowedIp(String username, String ipAddress) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.US001));
        String raw = (ipAddress != null) ? ipAddress.trim() : "";
        if (raw.isEmpty()) {
            throw new BadRequestException(ErrorCode.VA001, "IP address is required");
        }
        String ip = normalizeIp(raw);
        if (userAllowedIpRepository.existsByUserAndIpAddress(user, ip)) {
            return userAllowedIpRepository.findByUserOrderByCreatedAtDesc(user).stream()
                    .map(UserAllowedIp::getIpAddress)
                    .collect(Collectors.toList());
        }
        UserAllowedIp allowed = UserAllowedIp.builder()
                .user(user)
                .ipAddress(ip)
                .build();
        userAllowedIpRepository.save(allowed);
        log.info("Allowed IP {} for user {}", ip, username);
        return userAllowedIpRepository.findByUserOrderByCreatedAtDesc(user).stream()
                .map(UserAllowedIp::getIpAddress)
                .collect(Collectors.toList());
    }

    @Transactional
    public List<String> removeAllowedIp(String username, String ipAddress) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.US001));
        String raw = (ipAddress != null) ? ipAddress.trim() : "";
        if (raw.isEmpty()) {
            throw new BadRequestException(ErrorCode.VA001, "IP address is required");
        }
        String ip = normalizeIp(raw);
        userAllowedIpRepository.deleteByUserAndIpAddress(user, ip);
        log.info("Removed allowed IP {} for user {}", ip, username);
        return userAllowedIpRepository.findByUserOrderByCreatedAtDesc(user).stream()
                .map(UserAllowedIp::getIpAddress)
                .collect(Collectors.toList());
    }
}
