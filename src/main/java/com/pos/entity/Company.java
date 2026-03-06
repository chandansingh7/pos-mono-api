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

    private String taxId;

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

    private LocalDateTime updatedAt;

    private String updatedBy;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
