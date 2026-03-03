package com.pos.repository;

import com.pos.entity.AccessLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AccessLogRepository extends JpaRepository<AccessLog, Long> {

    Page<AccessLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<AccessLog> findByUsernameOrderByCreatedAtDesc(String username, Pageable pageable);

    List<AccessLog> findByUsername(String username);
}

