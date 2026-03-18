package com.pos.repository;

import com.pos.entity.Refund;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface RefundRepository extends JpaRepository<Refund, Long> {
    Optional<Refund> findFirstByOrderIdOrderByRefundedAtDesc(Long orderId);
    List<Refund> findAllByOrderIdOrderByRefundedAtDesc(Long orderId);
    List<Refund> findAllByOrderIdIn(List<Long> orderIds);

    @Query("SELECT COALESCE(SUM(r.amount), 0) FROM Refund r WHERE r.order.id = :orderId")
    BigDecimal sumAmountByOrderId(@Param("orderId") Long orderId);
}
