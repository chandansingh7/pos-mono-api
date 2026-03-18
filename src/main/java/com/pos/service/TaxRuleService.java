package com.pos.service;

import com.pos.dto.request.TaxRuleRequest;
import com.pos.dto.response.TaxRuleResponse;
import com.pos.entity.TaxRule;
import com.pos.exception.BadRequestException;
import com.pos.exception.ErrorCode;
import com.pos.exception.ResourceNotFoundException;
import com.pos.repository.ProductRepository;
import com.pos.repository.TaxRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaxRuleService {

    private final TaxRuleRepository taxRuleRepository;
    private final ProductRepository productRepository;

    @Transactional
    public void seedDefaults() {
        seedIfAbsent("STANDARD", "Standard Rate",  new BigDecimal("0.1000"));
        seedIfAbsent("REDUCED",  "Reduced Rate",   new BigDecimal("0.0500"));
        seedIfAbsent("EXEMPT",   "Tax Exempt",     BigDecimal.ZERO);
    }

    public List<TaxRuleResponse> getAll() {
        return taxRuleRepository.findAllByOrderByTaxCategoryAsc()
                .stream().map(TaxRuleResponse::from).collect(Collectors.toList());
    }

    public TaxRuleResponse getById(Long id) {
        return TaxRuleResponse.from(findById(id));
    }

    @Transactional
    public TaxRuleResponse create(TaxRuleRequest request) {
        String category = request.getTaxCategory().toUpperCase();
        if (taxRuleRepository.existsByTaxCategory(category)) {
            log.warn("[TX002] Tax category already exists: {}", category);
            throw new BadRequestException(ErrorCode.TX002);
        }
        TaxRule rule = taxRuleRepository.save(TaxRule.builder()
                .taxCategory(category)
                .label(request.getLabel())
                .rate(request.getRate())
                .build());
        log.info("Tax rule created: {} ({}) = {}%", category, request.getLabel(),
                request.getRate().multiply(BigDecimal.valueOf(100)));
        return TaxRuleResponse.from(rule);
    }

    @Transactional
    public TaxRuleResponse update(Long id, TaxRuleRequest request) {
        TaxRule rule = findById(id);
        String newCategory = request.getTaxCategory().toUpperCase();

        // If category key is changing, ensure uniqueness
        if (!rule.getTaxCategory().equals(newCategory)
                && taxRuleRepository.existsByTaxCategory(newCategory)) {
            throw new BadRequestException(ErrorCode.TX002);
        }

        rule.setTaxCategory(newCategory);
        rule.setLabel(request.getLabel());
        rule.setRate(request.getRate());
        log.info("Tax rule updated: id={} category={} rate={}", id, newCategory, request.getRate());
        return TaxRuleResponse.from(taxRuleRepository.save(rule));
    }

    @Transactional
    public void delete(Long id) {
        TaxRule rule = findById(id);
        long usedByProducts = productRepository.countByTaxCategory(rule.getTaxCategory());
        if (usedByProducts > 0) {
            log.warn("[TX003] Cannot delete tax rule '{}' — used by {} products",
                    rule.getTaxCategory(), usedByProducts);
            throw new BadRequestException(ErrorCode.TX003);
        }
        taxRuleRepository.delete(rule);
        log.info("Tax rule deleted: id={} category={}", id, rule.getTaxCategory());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private TaxRule findById(Long id) {
        return taxRuleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.TX001));
    }

    private void seedIfAbsent(String category, String label, BigDecimal rate) {
        if (!taxRuleRepository.existsByTaxCategory(category)) {
            taxRuleRepository.save(TaxRule.builder()
                    .taxCategory(category).label(label).rate(rate).build());
            log.info("Seeded default tax rule: {} = {}%", category,
                    rate.multiply(BigDecimal.valueOf(100)));
        }
    }
}
