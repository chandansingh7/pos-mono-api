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

    private String smtpProvider;
    private String smtpHost;
    private Integer smtpPort;
    private String smtpUsername;
    private Boolean smtpStartTls;
    /** True when an encrypted SMTP password is stored — never returns the actual password. */
    private Boolean smtpPasswordSet;
    private LocalDateTime emailVerifiedAt;

    private String emailSendMethod;
    private String msAccountEmail;
    private LocalDateTime msConnectedAt;

    private String taxId;
    /** Tax rate as a decimal fraction (e.g. 0.08 = 8%). Null → default 10%. */
    private BigDecimal taxRate;
    /** When false, tax is not applied to orders. Null treated as true. */
    private Boolean taxEnabled;
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

    /** Label field visibility defaults for printed labels. */
    private Boolean labelShowName;
    private Boolean labelShowSku;
    private Boolean labelShowPrice;

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

    /** Optional custom page width (mm) for CUSTOM label template. */
    private Integer labelPageWidthMm;

    /** Optional custom page height (mm) for CUSTOM label template. */
    private Integer labelPageHeightMm;

    /** Admin-controlled: allow dashboard when offline. */
    private Boolean offlineAllowDashboard;

    /** Admin-controlled: allow viewing orders when offline. */
    private Boolean offlineAllowOrders;

    /** Admin-controlled: allow POS when offline. */
    private Boolean offlineAllowPos;

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
                .smtpProvider(c.getSmtpProvider())
                .smtpHost(c.getSmtpHost())
                .smtpPort(c.getSmtpPort())
                .smtpUsername(c.getSmtpUsername())
                .smtpStartTls(c.getSmtpStartTls())
                .smtpPasswordSet(c.getSmtpPasswordEncrypted() != null && !c.getSmtpPasswordEncrypted().isBlank())
                .emailVerifiedAt(c.getEmailVerifiedAt())
                .emailSendMethod(c.getEmailSendMethod())
                .msAccountEmail(c.getMsAccountEmail())
                .msConnectedAt(c.getMsConnectedAt())
                .taxId(c.getTaxId())
                .taxRate(c.getTaxRate())
                .taxEnabled(c.getTaxEnabled())
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
                .labelShowName(c.getLabelShowName())
                .labelShowSku(c.getLabelShowSku())
                .labelShowPrice(c.getLabelShowPrice())
                .labelTemplateId(c.getLabelTemplateId())
                .labelTemplateColumns(c.getLabelTemplateColumns())
                .labelTemplateRows(c.getLabelTemplateRows())
                .labelTemplateGapMm(c.getLabelTemplateGapMm())
                .labelTemplatePagePaddingMm(c.getLabelTemplatePagePaddingMm())
                .labelTemplateLabelPaddingMm(c.getLabelTemplateLabelPaddingMm())
                .labelPageWidthMm(c.getLabelPageWidthMm())
                .labelPageHeightMm(c.getLabelPageHeightMm())
                .offlineAllowDashboard(c.getOfflineAllowDashboard())
                .offlineAllowOrders(c.getOfflineAllowOrders())
                .offlineAllowPos(c.getOfflineAllowPos())
                .updatedAt(c.getUpdatedAt())
                .build();
    }
}
