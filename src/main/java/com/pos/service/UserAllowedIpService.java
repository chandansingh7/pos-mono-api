package com.pos.service;

import com.pos.entity.User;
import com.pos.entity.UserAllowedIp;
import com.pos.exception.BadRequestException;
import com.pos.exception.ErrorCode;
import com.pos.exception.ResourceNotFoundException;
import com.pos.repository.UserAllowedIpRepository;
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

    /**
     * Returns true if this user is allowed to access from the given IP.
     * If the user has no allowed IPs configured, all IPs are allowed.
     */
    @Transactional(readOnly = true)
    public boolean isAllowed(Long userId, String clientIp) {
        if (userId == null || clientIp == null || clientIp.isBlank()) {
            return true;
        }
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return true;
        List<UserAllowedIp> allowed = userAllowedIpRepository.findByUserOrderByCreatedAtDesc(user);
        if (allowed.isEmpty()) {
            return true;
        }
        String normalized = clientIp.trim();
        return allowed.stream().anyMatch(a -> normalized.equals(a.getIpAddress().trim()));
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
        String ip = (ipAddress != null) ? ipAddress.trim() : "";
        if (ip.isEmpty()) {
            throw new BadRequestException(ErrorCode.VA001, "IP address is required");
        }
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
        String ip = (ipAddress != null) ? ipAddress.trim() : "";
        if (ip.isEmpty()) {
            throw new BadRequestException(ErrorCode.VA001, "IP address is required");
        }
        userAllowedIpRepository.deleteByUserAndIpAddress(user, ip);
        log.info("Removed allowed IP {} for user {}", ip, username);
        return userAllowedIpRepository.findByUserOrderByCreatedAtDesc(user).stream()
                .map(UserAllowedIp::getIpAddress)
                .collect(Collectors.toList());
    }
}
