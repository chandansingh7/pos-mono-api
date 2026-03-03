package com.pos.repository;

import com.pos.entity.Shift;
import com.pos.entity.User;
import com.pos.enums.ShiftStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ShiftRepository extends JpaRepository<Shift, Long> {

    Optional<Shift> findByCashierAndStatus(User cashier, ShiftStatus status);

    List<Shift> findByCashierAndOpenedAtBetween(User cashier, LocalDateTime from, LocalDateTime to);

    long countByStatus(ShiftStatus status);

    List<Shift> findAllByOrderByOpenedAtDesc(org.springframework.data.domain.Pageable pageable);
}

