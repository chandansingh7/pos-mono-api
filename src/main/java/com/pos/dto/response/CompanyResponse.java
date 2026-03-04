package com.pos.dto.response;

import com.pos.entity.Company;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class CompanyResponse {

    private Long id;
    private String name;
    private String logoUrl;
    private String faviconUrl;
    private String address;
    private String phone;
    private String email;
    private String taxId;
    private String website;
    private String receiptFooterText;
    private String receiptPaperSize;
    private String receiptHeaderText;
    private String displayCurrency;
    private String locale;
    private LocalDateTime updatedAt;

    /** Whether quick shift open/close controls are enabled on the POS screen. */
    private Boolean posQuickShiftControls;

    /** Per-company override for maximum allowed absolute cash difference when closing a shift. */
    private BigDecimal shiftMaxDifferenceAbsolute;

    /** Per-company override for minimum open minutes before close is allowed. */
    private Long shiftMinOpenMinutes;

    /** Per-company override for maximum open hours before close is blocked. */
    private Long shiftMaxOpenHours;

    /** Per-company override for requiring same-day shift close. */
    private Boolean shiftRequireSameDay;

    public static CompanyResponse from(Company c) {
        if (c == null) return null;
        return CompanyResponse.builder()
                .id(c.getId())
                .name(c.getName())
                .logoUrl(c.getLogoUrl())
                .faviconUrl(c.getFaviconUrl())
                .address(c.getAddress())
                .phone(c.getPhone())
                .email(c.getEmail())
                .taxId(c.getTaxId())
                .website(c.getWebsite())
                .receiptFooterText(c.getReceiptFooterText())
                .receiptPaperSize(c.getReceiptPaperSize())
                .receiptHeaderText(c.getReceiptHeaderText())
                .displayCurrency(c.getDisplayCurrency())
                .locale(c.getLocale())
                .posQuickShiftControls(c.getPosQuickShiftControls())
                .shiftMaxDifferenceAbsolute(c.getShiftMaxDifferenceAbsolute())
                .shiftMinOpenMinutes(c.getShiftMinOpenMinutes())
                .shiftMaxOpenHours(c.getShiftMaxOpenHours())
                .shiftRequireSameDay(c.getShiftRequireSameDay())
                .updatedAt(c.getUpdatedAt())
                .build();
    }
}
