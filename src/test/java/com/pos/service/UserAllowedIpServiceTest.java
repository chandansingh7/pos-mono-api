package com.pos.service;

import com.pos.entity.User;
import com.pos.entity.UserAllowedIp;
import com.pos.enums.Role;
import com.pos.exception.BadRequestException;
import com.pos.exception.ResourceNotFoundException;
import com.pos.entity.UserBlockedIp;
import com.pos.repository.UserAllowedIpRepository;
import com.pos.repository.UserBlockedIpRepository;
import com.pos.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserAllowedIpServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserAllowedIpRepository userAllowedIpRepository;
    @Mock
    private UserBlockedIpRepository userBlockedIpRepository;

    @InjectMocks
    private UserAllowedIpService userAllowedIpService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setUsername("admin1");
        user.setEmail("admin@example.com");
        user.setRole(Role.ADMIN);
    }

    @Test
    void isAllowed_returnsTrueWhenUserHasNoAllowedIps() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userBlockedIpRepository.findByUserOrderByCreatedAtDesc(user)).thenReturn(List.of());
        when(userAllowedIpRepository.findByUserOrderByCreatedAtDesc(user)).thenReturn(List.of());

        assertTrue(userAllowedIpService.isAllowed(1L, "192.168.1.1"));
    }

    @Test
    void isAllowed_returnsTrueWhenIpIsInAllowList() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userBlockedIpRepository.findByUserOrderByCreatedAtDesc(user)).thenReturn(List.of());
        UserAllowedIp allowed = UserAllowedIp.builder().user(user).ipAddress("192.168.1.1").build();
        when(userAllowedIpRepository.findByUserOrderByCreatedAtDesc(user)).thenReturn(List.of(allowed));

        assertTrue(userAllowedIpService.isAllowed(1L, "192.168.1.1"));
    }

    @Test
    void isAllowed_returnsFalseWhenIpNotInAllowList() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userBlockedIpRepository.findByUserOrderByCreatedAtDesc(user)).thenReturn(List.of());
        UserAllowedIp allowed = UserAllowedIp.builder().user(user).ipAddress("192.168.1.1").build();
        when(userAllowedIpRepository.findByUserOrderByCreatedAtDesc(user)).thenReturn(List.of(allowed));

        assertFalse(userAllowedIpService.isAllowed(1L, "10.0.0.1"));
    }

    @Test
    void isAllowed_returnsFalseWhenIpIsBlocked() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        UserBlockedIp blocked = UserBlockedIp.builder().user(user).ipAddress("192.168.1.1").build();
        when(userBlockedIpRepository.findByUserOrderByCreatedAtDesc(user)).thenReturn(List.of(blocked));
        when(userAllowedIpRepository.findByUserOrderByCreatedAtDesc(user)).thenReturn(List.of());

        assertFalse(userAllowedIpService.isAllowed(1L, "192.168.1.1"));
    }

    @Test
    void getAllowedIpsByUsername_throwsWhenUserNotFound() {
        when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> userAllowedIpService.getAllowedIpsByUsername("unknown"));
    }

    @Test
    void addAllowedIp_addsNewIpAndReturnsList() {
        when(userRepository.findByUsername("admin1")).thenReturn(Optional.of(user));
        when(userAllowedIpRepository.existsByUserAndIpAddress(user, "192.168.1.1")).thenReturn(false);
        when(userAllowedIpRepository.findByUserOrderByCreatedAtDesc(user)).thenReturn(List.of());

        userAllowedIpService.addAllowedIp("admin1", "192.168.1.1");

        verify(userAllowedIpRepository).save(argThat(a -> "192.168.1.1".equals(a.getIpAddress()) && a.getUser() == user));
    }

    @Test
    void addAllowedIp_throwsWhenIpBlank() {
        when(userRepository.findByUsername("admin1")).thenReturn(Optional.of(user));

        assertThrows(BadRequestException.class, () -> userAllowedIpService.addAllowedIp("admin1", "  "));
        verify(userAllowedIpRepository, never()).save(any());
    }
}
