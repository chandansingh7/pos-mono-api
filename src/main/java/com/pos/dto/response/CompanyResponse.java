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
    /** ISO country code (e.g. US, IN). Used to pre-select weight unit. */
    private String countryCode;
    /** Weight unit: kg or lb. */
    private String weightUnit;
    /** Volume unit: L or gal. */
    private String volumeUnit;
    private LocalDateTime updatedAt;

    /** Whether quick shift open/close controls are enabled on the POS screen. */
    private Boolean posQuickShiftControls;

    /** POS layout: "grid" or "scan". */
    private String posLayout;

    /** Per-company override for maximum allowed absolute cash difference when closing a shift. */
    private BigDecimal shiftMaxDifferenceAbsolute;

    /** Per-company override for minimum open minutes before close is allowed. */
    private Long shiftMinOpenMinutes;

    /** Per-company override for maximum open hours before close is blocked. */
    private Long shiftMaxOpenHours;

    /** Per-company override for requiring same-day shift close. */
    private Boolean shiftRequireSameDay;

    /** Default label layout template for price labels (A4_2x4, A4_2x5, A4_3x4, CUSTOM). */
    private String labelTemplateId;
    /** Custom layout: columns for CUSTOM template. */
    private Integer labelTemplateColumns;
    /** Custom layout: rows for CUSTOM template. */
    private Integer labelTemplateRows;
    /** Custom layout: gap (mm) between labels for CUSTOM template. */
    private Integer labelTemplateGapMm;
    /** Custom layout: page padding (mm) for CUSTOM template. */
    private Integer labelTemplatePagePaddingMm;
    /** Custom layout: label padding (mm) for CUSTOM template. */
    private Integer labelTemplateLabelPaddingMm;

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
                .countryCode(c.getCountryCode())
                .weightUnit(c.getWeightUnit())
                .volumeUnit(c.getVolumeUnit())
                .posQuickShiftControls(c.getPosQuickShiftControls())
                .posLayout(c.getPosLayout())
                .shiftMaxDifferenceAbsolute(c.getShiftMaxDifferenceAbsolute())
                .shiftMinOpenMinutes(c.getShiftMinOpenMinutes())
                .shiftMaxOpenHours(c.getShiftMaxOpenHours())
                .shiftRequireSameDay(c.getShiftRequireSameDay())
                .labelTemplateId(c.getLabelTemplateId())
                .labelTemplateColumns(c.getLabelTemplateColumns())
                .labelTemplateRows(c.getLabelTemplateRows())
                .labelTemplateGapMm(c.getLabelTemplateGapMm())
                .labelTemplatePagePaddingMm(c.getLabelTemplatePagePaddingMm())
                .labelTemplateLabelPaddingMm(c.getLabelTemplateLabelPaddingMm())
                .updatedAt(c.getUpdatedAt())
                .build();
    }
}
