package com.pos.service;

import com.pos.dto.request.TaxRuleRequest;
import com.pos.dto.response.TaxRuleResponse;
import com.pos.entity.TaxRule;
import com.pos.exception.BadRequestException;
import com.pos.exception.ResourceNotFoundException;
import com.pos.repository.ProductRepository;
import com.pos.repository.TaxRuleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaxRuleServiceTest {

    @Mock private TaxRuleRepository taxRuleRepository;
    @Mock private ProductRepository productRepository;

    @InjectMocks
    private TaxRuleService taxRuleService;

    private TaxRule sampleRule() {
        return TaxRule.builder()
                .id(1L)
                .taxCategory("STANDARD")
                .label("Standard Rate")
                .rate(new BigDecimal("0.10"))
                .build();
    }

    @Test
    void getAll_returnsList() {
        when(taxRuleRepository.findAllByOrderByTaxCategoryAsc()).thenReturn(List.of(sampleRule()));
        List<TaxRuleResponse> result = taxRuleService.getAll();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTaxCategory()).isEqualTo("STANDARD");
    }

    @Test
    void create_newCategory_savesAndReturns() {
        when(taxRuleRepository.existsByTaxCategory("FOOD")).thenReturn(false);
        when(taxRuleRepository.save(any())).thenAnswer(inv -> {
            TaxRule r = inv.getArgument(0);
            r.setId(10L);
            return r;
        });
        TaxRuleRequest req = new TaxRuleRequest();
        req.setTaxCategory("FOOD");
        req.setLabel("Food Rate");
        req.setRate(new BigDecimal("0.05"));

        TaxRuleResponse response = taxRuleService.create(req);
        assertThat(response.getTaxCategory()).isEqualTo("FOOD");
        assertThat(response.getRate()).isEqualByComparingTo("0.05");
    }

    @Test
    void create_duplicateCategory_throwsBadRequest() {
        when(taxRuleRepository.existsByTaxCategory("STANDARD")).thenReturn(true);
        TaxRuleRequest req = new TaxRuleRequest();
        req.setTaxCategory("STANDARD");
        req.setLabel("Standard Rate");
        req.setRate(new BigDecimal("0.10"));

        assertThatThrownBy(() -> taxRuleService.create(req))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void delete_usedByProducts_throwsBadRequest() {
        when(taxRuleRepository.findById(1L)).thenReturn(Optional.of(sampleRule()));
        when(productRepository.countByTaxCategory("STANDARD")).thenReturn(3L);

        assertThatThrownBy(() -> taxRuleService.delete(1L))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void delete_notUsedByProducts_deletesSuccessfully() {
        when(taxRuleRepository.findById(1L)).thenReturn(Optional.of(sampleRule()));
        when(productRepository.countByTaxCategory("STANDARD")).thenReturn(0L);
        doNothing().when(taxRuleRepository).delete(any());

        taxRuleService.delete(1L);
        verify(taxRuleRepository, times(1)).delete(any(TaxRule.class));
    }

    @Test
    void getById_nonExistent_throwsNotFoundException() {
        when(taxRuleRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> taxRuleService.getById(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void seedDefaults_whenNoneExist_createsThreeDefaultRules() {
        when(taxRuleRepository.existsByTaxCategory(anyString())).thenReturn(false);
        when(taxRuleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        taxRuleService.seedDefaults();
        verify(taxRuleRepository, times(3)).save(any(TaxRule.class));
    }

    @Test
    void seedDefaults_whenAllExist_skipsAll() {
        when(taxRuleRepository.existsByTaxCategory(anyString())).thenReturn(true);

        taxRuleService.seedDefaults();
        verify(taxRuleRepository, never()).save(any());
    }
}
