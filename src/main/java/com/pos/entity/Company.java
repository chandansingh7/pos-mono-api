package com.pos.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Single-row company profile: name, contact, and receipt/bill settings.
 * Used for logo display, bills, and printer options.
 */
@Entity
@Table(name = "company")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String logoUrl;

    private String faviconUrl;

    private String address;

    private String phone;

    private String email;

    /** SMTP provider preset: GMAIL, OUTLOOK, or CUSTOM. When set, host/port are pre-filled for GMAIL/OUTLOOK. */
    @Column(name = "smtp_provider", length = 20)
    private String smtpProvider;

    @Column(name = "smtp_host", length = 255)
    private String smtpHost;

    @Column(name = "smtp_port")
    private Integer smtpPort;

    @Column(name = "smtp_username", length = 255)
    private String smtpUsername;

    /** Encrypted SMTP password (AES-GCM); decrypted at runtime using SMTP_ENCRYPTION_KEY. */
    @Column(name = "smtp_password_encrypted", columnDefinition = "TEXT")
    private String smtpPasswordEncrypted;

    @Column(name = "smtp_start_tls")
    private Boolean smtpStartTls;

    /** Set when SMTP was last successfully verified (test email sent). */
    @Column(name = "email_verified_at")
    private LocalDateTime emailVerifiedAt;

    /**
     * Email sending method for receipts: SMTP or MICROSOFT (OAuth + Graph).
     * Defaults to SMTP.
     */
    @Column(name = "email_send_method", length = 20)
    private String emailSendMethod;

    /** Microsoft Graph delegated refresh token (encrypted). */
    @Column(name = "ms_refresh_token_encrypted", columnDefinition = "TEXT")
    private String msRefreshTokenEncrypted;

    /** Microsoft account email that is connected (for display/status). */
    @Column(name = "ms_account_email", length = 255)
    private String msAccountEmail;

    /** Set when Microsoft connection was last verified (Graph test email sent). */
    @Column(name = "ms_connected_at")
    private LocalDateTime msConnectedAt;

    private String taxId;

    /**
     * Tax rate as a decimal fraction (e.g. 0.08 = 8%, 0.10 = 10%).
     * Null → backward-compatible default of 10% is applied at order creation.
     */
    @Column(name = "tax_rate", precision = 6, scale = 4)
    private BigDecimal taxRate;

    /**
     * When false, no tax is applied to any order. Null is treated as true (tax enabled).
     */
    @Column(name = "tax_enabled")
    private Boolean taxEnabled;

    private String website;

    /** Optional footer line on receipts (e.g. "Thank you!") */
    private String receiptFooterText;

    /** Receipt paper size: 58mm, 80mm, A4 */
    @Column(name = "receipt_paper_size", length = 20)
    private String receiptPaperSize;

    /** Optional custom header line on receipts */
    private String receiptHeaderText;

    /** Display currency code for prices and reports (e.g. USD, INR, EUR). Default USD. */
    @Column(name = "display_currency", length = 6)
    private String displayCurrency;

    /** Locale for number/date formatting (e.g. en-US, hi-IN). Affects UI formatting. */
    @Column(length = 10)
    private String locale;

    /** ISO 3166-1 alpha-2 country code (e.g. US, IN, GB). Used to pre-select weight unit. */
    @Column(name = "country_code", length = 2)
    private String countryCode;

    /** Weight unit for products/receipts: kg (metric) or lb (imperial). Defaults by country when set. */
    @Column(name = "weight_unit", length = 4)
    private String weightUnit;

    /** Default volume unit for products: L (metric) or gal (US gallon). Defaults by country when set. */
    @Column(name = "volume_unit", length = 6)
    private String volumeUnit;

    /**
     * When true, show quick shift open/close controls directly on the POS / Cashier
     * screen for cashiers (in addition to the Shifts page).
     */
    @Column(name = "pos_quick_shift_controls")
    private Boolean posQuickShiftControls;

    /**
     * POS screen layout: "grid" (product grid + separate search/barcode) or "scan"
     * (product list at top + single search/key-in field; barcode match adds directly to cart).
     */
    @Column(name = "pos_layout", length = 20)
    private String posLayout;

    /**
     * Optional per-company override for maximum allowed absolute cash difference
     * when closing a shift. If null, falls back to environment config; if 0,
     * no restriction is enforced.
     */
    @Column(name = "shift_max_difference_abs", precision = 19, scale = 2)
    private BigDecimal shiftMaxDifferenceAbsolute;

    /**
     * Optional minimum number of minutes a shift must be open before it can be closed.
     * If null, falls back to environment config; if 0, no minimum duration is enforced.
     */
    @Column(name = "shift_min_open_minutes")
    private Long shiftMinOpenMinutes;

    /**
     * Optional maximum number of hours a shift is allowed to remain open.
     * If null, falls back to environment config; if 0, no maximum duration is enforced.
     */
    @Column(name = "shift_max_open_hours")
    private Long shiftMaxOpenHours;

    /**
     * Optional per-company flag requiring shifts to be closed on the same
     * calendar day they were opened. If null, falls back to environment
     * config; when false, cross-day closes are allowed.
     */
    @Column(name = "shift_require_same_day")
    private Boolean shiftRequireSameDay;

    /**
     * Label field visibility defaults for printed labels.
     */
    @Column(name = "label_show_name")
    private Boolean labelShowName;

    @Column(name = "label_show_sku")
    private Boolean labelShowSku;

    @Column(name = "label_show_price")
    private Boolean labelShowPrice;

    /**
     * Default label layout template for price labels (A4_2x4, A4_2x5, A4_3x4, CUSTOM).
     * Used by the Labels screen when printing.
     */
    @Column(name = "label_template_id", length = 32)
    private String labelTemplateId;

    /** Custom layout: columns for CUSTOM template. */
    @Column(name = "label_template_columns")
    private Integer labelTemplateColumns;

    /** Custom layout: rows for CUSTOM template. */
    @Column(name = "label_template_rows")
    private Integer labelTemplateRows;

    /** Custom layout: gap (mm) between labels for CUSTOM template. */
    @Column(name = "label_template_gap_mm")
    private Integer labelTemplateGapMm;

    /** Custom layout: page padding (mm) for CUSTOM template. */
    @Column(name = "label_template_page_padding_mm")
    private Integer labelTemplatePagePaddingMm;

    /** Custom layout: label padding (mm) for CUSTOM template. */
    @Column(name = "label_template_label_padding_mm")
    private Integer labelTemplateLabelPaddingMm;

    /** Optional custom page width (mm) for CUSTOM label template. */
    @Column(name = "label_page_width_mm")
    private Integer labelPageWidthMm;

    /** Optional custom page height (mm) for CUSTOM label template. */
    @Column(name = "label_page_height_mm")
    private Integer labelPageHeightMm;

    /**
     * Admin-controlled offline behaviour: allow dashboard when offline.
     * When false, dashboard is blocked when device has no network.
     */
    @Column(name = "offline_allow_dashboard")
    private Boolean offlineAllowDashboard;

    /**
     * Admin-controlled offline behaviour: allow viewing orders list when offline (cached data).
     */
    @Column(name = "offline_allow_orders")
    private Boolean offlineAllowOrders;

    /**
     * Admin-controlled offline behaviour: allow POS when offline (place orders, sync when online).
     */
    @Column(name = "offline_allow_pos")
    private Boolean offlineAllowPos;

    private LocalDateTime updatedAt;

    private String updatedBy;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
