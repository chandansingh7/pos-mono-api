package com.pos.service;

import com.pos.entity.User;
import com.pos.entity.UserBlockedIp;
import com.pos.exception.BadRequestException;
import com.pos.exception.ErrorCode;
import com.pos.exception.ResourceNotFoundException;
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
public class UserBlockedIpService {

    private final UserRepository userRepository;
    private final UserBlockedIpRepository userBlockedIpRepository;

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

    @Transactional(readOnly = true)
    public boolean isBlocked(Long userId, String clientIp) {
        if (userId == null || clientIp == null || clientIp.isBlank()) return false;
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return false;
        String normalized = normalizeIp(clientIp);
        return userBlockedIpRepository.findByUserOrderByCreatedAtDesc(user).stream()
                .anyMatch(b -> normalized.equals(normalizeIp(b.getIpAddress())));
    }

    @Transactional(readOnly = true)
    public List<String> getBlockedIpsByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.US001));
        return userBlockedIpRepository.findByUserOrderByCreatedAtDesc(user).stream()
                .map(UserBlockedIp::getIpAddress)
                .collect(Collectors.toList());
    }

    @Transactional
    public List<String> addBlockedIp(String username, String ipAddress, String currentUsername, String clientIp) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.US001));
        String raw = (ipAddress != null) ? ipAddress.trim() : "";
        if (raw.isEmpty()) {
            throw new BadRequestException(ErrorCode.VA001, "IP address is required");
        }
        String ip = normalizeIp(raw);
        if (username.equals(currentUsername) && ip.equals(normalizeIp(clientIp))) {
            log.warn("[AU009] User {} attempted to block their own current IP {}", username, ip);
            throw new BadRequestException(ErrorCode.AU009);
        }
        if (userBlockedIpRepository.existsByUserAndIpAddress(user, ip)) {
            return userBlockedIpRepository.findByUserOrderByCreatedAtDesc(user).stream()
                    .map(UserBlockedIp::getIpAddress)
                    .collect(Collectors.toList());
        }
        UserBlockedIp blocked = UserBlockedIp.builder()
                .user(user)
                .ipAddress(ip)
                .build();
        userBlockedIpRepository.save(blocked);
        log.info("Blocked IP {} for user {}", ip, username);
        return userBlockedIpRepository.findByUserOrderByCreatedAtDesc(user).stream()
                .map(UserBlockedIp::getIpAddress)
                .collect(Collectors.toList());
    }

    @Transactional
    public List<String> removeBlockedIp(String username, String ipAddress) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.US001));
        String raw = (ipAddress != null) ? ipAddress.trim() : "";
        if (raw.isEmpty()) {
            throw new BadRequestException(ErrorCode.VA001, "IP address is required");
        }
        String ip = normalizeIp(raw);
        userBlockedIpRepository.deleteByUserAndIpAddress(user, ip);
        log.info("Unblocked IP {} for user {}", ip, username);
        return userBlockedIpRepository.findByUserOrderByCreatedAtDesc(user).stream()
                .map(UserBlockedIp::getIpAddress)
                .collect(Collectors.toList());
    }
}
