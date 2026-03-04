package com.pos.repository;

import com.pos.entity.User;
import com.pos.entity.UserAllowedIp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserAllowedIpRepository extends JpaRepository<UserAllowedIp, Long> {

    List<UserAllowedIp> findByUserOrderByCreatedAtDesc(User user);

    Optional<UserAllowedIp> findByUserAndIpAddress(User user, String ipAddress);

    boolean existsByUserAndIpAddress(User user, String ipAddress);

    void deleteByUserAndIpAddress(User user, String ipAddress);
}
