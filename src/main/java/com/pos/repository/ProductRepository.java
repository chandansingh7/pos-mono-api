package com.pos.repository;

import com.pos.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    @Query("SELECT p.sku FROM Product p WHERE p.sku IN :skus")
    List<String> findSkusBySkuIn(@Param("skus") List<String> skus);
    Optional<Product> findByBarcode(String barcode);
    Optional<Product> findBySku(String sku);
    boolean existsBySku(String sku);
    boolean existsByBarcode(String barcode);

    @Query("SELECT p FROM Product p WHERE p.active = true AND " +
           "(LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(p.sku) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<Product> searchActive(@Param("query") String query, Pageable pageable);

    Page<Product> findByActiveTrue(Pageable pageable);
    Page<Product> findByCategoryIdAndActiveTrue(Long categoryId, Pageable pageable);
    List<Product> findByCategoryId(Long categoryId);

    // ── Stats ──────────────────────────────────────────────────────────────────
    long countByActiveTrue();
    long countByActiveFalse();
    long countByTaxCategory(String taxCategory);
}
