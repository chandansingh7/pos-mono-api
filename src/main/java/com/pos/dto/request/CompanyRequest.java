package com.pos.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CompanyRequest {

    @NotBlank(message = "Company name is required")
    private String name;

    private String logoUrl;
    private String faviconUrl;
    private String address;
    private String phone;
    private String email;

    /** SMTP provider: GMAIL, OUTLOOK, or CUSTOM. Host/port are auto-filled for GMAIL/OUTLOOK. */
    private String smtpProvider;
    private String smtpHost;
    private Integer smtpPort;
    private String smtpUsername;
    /** Plain SMTP password (only sent when saving; never returned). Stored encrypted. */
    private String smtpPassword;
    private Boolean smtpStartTls;

    /** Email send method: SMTP or MICROSOFT. */
    private String emailSendMethod;

    private String taxId;
    /** Tax rate as a decimal fraction (0.08 = 8%). Null → default 10%. */
    private BigDecimal taxRate;
    /** When false, no tax is applied to new orders. */
    private Boolean taxEnabled;
    private String website;
    private String receiptFooterText;
    private String receiptHeaderText;

    /** Receipt paper size: 58mm, 80mm, A4 */
    private String receiptPaperSize;

    /** Display currency code (e.g. USD, INR, EUR) */
    private String displayCurrency;

    /** Locale for formatting (e.g. en-US, hi-IN) */
    private String locale;

    /** ISO 3166-1 alpha-2 country code (e.g. US, IN). Used to pre-select weight unit. */
    private String countryCode;

    /** Weight unit for products: kg or lb. */
    private String weightUnit;

    /** Volume unit for products: L or gal (US gallon). */
    private String volumeUnit;

    /**
     * Enable quick shift open/close controls on the POS / Cashier screen.
     * When false or null, cashiers must use the Shifts page to manage shifts.
     */
    private Boolean posQuickShiftControls;

    /**
     * POS layout: "grid" (default) or "scan". Scan layout shows product list at top
     * and a single "Search or key in item" field; barcode match adds directly to cart.
     */
    private String posLayout;

    /**
     * Maximum absolute cash difference (over/short) allowed when closing a shift.
     * If {@code null} the environment default is used; if {@code 0}, no restriction
     * is enforced.
     */
    private BigDecimal shiftMaxDifferenceAbsolute;

    /**
     * Minimum number of minutes a shift must be open before it can be closed.
     * If {@code null} the environment default is used; if {@code 0}, no minimum
     * duration is enforced.
     */
    private Long shiftMinOpenMinutes;

    /**
     * Maximum number of hours a shift is allowed to remain open.
     * If {@code null} the environment default is used; if {@code 0}, no maximum
     * duration is enforced.
     */
    private Long shiftMaxOpenHours;

    /**
     * When true, disallow closing a shift that was opened on a previous calendar
     * day. If {@code null}, the environment default is used.
     */
    private Boolean shiftRequireSameDay;

    /**
     * Label field visibility defaults for printed labels.
     */
    private Boolean labelShowName;
    private Boolean labelShowSku;
    private Boolean labelShowPrice;

    /**
     * Default label layout template for price labels (A4_2x4, A4_2x5, A4_3x4, CUSTOM).
     * Used by the Labels screen when printing.
     */
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

    /** Admin-controlled: allow POS when offline (place orders, sync when online). */
    private Boolean offlineAllowPos;
}
