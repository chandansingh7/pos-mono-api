package com.pos.entity;

import jakarta.persistence.*;
import lombok.*;

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

    private LocalDateTime updatedAt;

    private String updatedBy;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
