package com.pos.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CompanyRequest {

    @NotBlank(message = "Company name is required")
    private String name;

    private String logoUrl;
    private String faviconUrl;
    private String address;
    private String phone;
    private String email;
    private String taxId;
    private String website;
    private String receiptFooterText;
    private String receiptHeaderText;

    /** Receipt paper size: 58mm, 80mm, A4 */
    private String receiptPaperSize;

    /** Display currency code (e.g. USD, INR, EUR) */
    private String displayCurrency;

    /** Locale for formatting (e.g. en-US, hi-IN) */
    private String locale;

    /**
     * Enable quick shift open/close controls on the POS / Cashier screen.
     * When false or null, cashiers must use the Shifts page to manage shifts.
     */
    private Boolean posQuickShiftControls;
}
