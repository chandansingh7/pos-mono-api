package com.pos.dto.response;

import com.pos.entity.TaxRule;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class TaxRuleResponse {
    private Long id;
    private String taxCategory;
    private String label;
    private BigDecimal rate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static TaxRuleResponse from(TaxRule r) {
        return TaxRuleResponse.builder()
                .id(r.getId())
                .taxCategory(r.getTaxCategory())
                .label(r.getLabel())
                .rate(r.getRate())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }
}
