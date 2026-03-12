package com.pos.repository;

import com.pos.entity.Inventory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {
    Optional<Inventory> findByProductId(Long productId);

    @Query(value = "SELECT i FROM Inventory i JOIN FETCH i.product", countQuery = "SELECT COUNT(i) FROM Inventory i")
    Page<Inventory> findAllWithProduct(Pageable pageable);

    @Query(value = "SELECT i FROM Inventory i JOIN FETCH i.product p WHERE LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(p.sku) LIKE LOWER(CONCAT('%', :search, '%'))",
           countQuery = "SELECT COUNT(i) FROM Inventory i JOIN i.product p WHERE LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(p.sku) LIKE LOWER(CONCAT('%', :search, '%'))")
    Page<Inventory> findAllWithProductSearch(@Param("search") String search, Pageable pageable);

    @Query("SELECT i FROM Inventory i WHERE i.quantity <= i.lowStockThreshold")
    List<Inventory> findLowStockItems();

    @Query("SELECT i FROM Inventory i WHERE i.quantity = 0")
    List<Inventory> findOutOfStockItems();

    // ── Stats ──────────────────────────────────────────────────────────────────
    @Query("SELECT COUNT(i) FROM Inventory i WHERE i.quantity > i.lowStockThreshold")
    long countInStock();

    @Query("SELECT COUNT(i) FROM Inventory i WHERE i.quantity > 0 AND i.quantity <= i.lowStockThreshold")
    long countLowStock();

    @Query("SELECT COUNT(i) FROM Inventory i WHERE i.quantity = 0")
    long countOutOfStock();
}
