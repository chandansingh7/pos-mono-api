package com.pos.service;

import com.pos.config.ShiftConfig;
import com.pos.entity.Company;
import com.pos.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Resolves effective shift rules by combining environment-level defaults
 * from {@link ShiftConfig} with optional per-company overrides stored on
 * the {@link Company} entity.
 */
@Component
@RequiredArgsConstructor
public class ShiftRuleProvider {

    private final CompanyRepository companyRepository;
    private final ShiftConfig shiftConfig;

    private Company currentCompany() {
        return companyRepository.findFirstByOrderByIdAsc().orElse(null);
    }

    public BigDecimal maxDifferenceAbsolute() {
        Company c = currentCompany();
        if (c != null && c.getShiftMaxDifferenceAbsolute() != null) {
            return c.getShiftMaxDifferenceAbsolute();
        }
        return shiftConfig.getMaxDifferenceAbsolute();
    }

    public long minOpenMinutes() {
        Company c = currentCompany();
        if (c != null && c.getShiftMinOpenMinutes() != null) {
            return c.getShiftMinOpenMinutes();
        }
        return shiftConfig.getMinOpenMinutes();
    }

    public long maxOpenHours() {
        Company c = currentCompany();
        if (c != null && c.getShiftMaxOpenHours() != null) {
            return c.getShiftMaxOpenHours();
        }
        return shiftConfig.getMaxOpenHours();
    }

    public boolean requireSameDay() {
        Company c = currentCompany();
        if (c != null && c.getShiftRequireSameDay() != null) {
            return c.getShiftRequireSameDay();
        }
        return shiftConfig.isRequireSameDay();
    }
}

