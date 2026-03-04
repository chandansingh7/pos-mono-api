package com.pos.repository;

import com.pos.entity.User;
import com.pos.entity.UserBlockedIp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserBlockedIpRepository extends JpaRepository<UserBlockedIp, Long> {

    List<UserBlockedIp> findByUserOrderByCreatedAtDesc(User user);

    Optional<UserBlockedIp> findByUserAndIpAddress(User user, String ipAddress);

    boolean existsByUserAndIpAddress(User user, String ipAddress);

    void deleteByUserAndIpAddress(User user, String ipAddress);
}
