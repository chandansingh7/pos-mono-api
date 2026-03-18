package com.pos.repository;

import com.pos.entity.TaxRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TaxRuleRepository extends JpaRepository<TaxRule, Long> {
    Optional<TaxRule> findByTaxCategory(String taxCategory);
    boolean existsByTaxCategory(String taxCategory);
    List<TaxRule> findAllByOrderByTaxCategoryAsc();
}
