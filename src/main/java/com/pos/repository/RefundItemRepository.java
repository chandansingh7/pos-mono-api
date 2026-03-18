package com.pos.repository;

import com.pos.entity.RefundItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RefundItemRepository extends JpaRepository<RefundItem, Long> {
    @Query("SELECT ri FROM RefundItem ri JOIN FETCH ri.orderItem oi JOIN FETCH oi.product WHERE ri.refund.order.id = :orderId")
    List<RefundItem> findAllByRefundOrderId(@Param("orderId") Long orderId);

    @Query("SELECT COALESCE(SUM(ri.quantity), 0) FROM RefundItem ri WHERE ri.orderItem.id = :orderItemId")
    java.math.BigDecimal sumRefundedQuantityByOrderItemId(@Param("orderItemId") Long orderItemId);
}
