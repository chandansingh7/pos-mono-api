package com.pos.backup;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyExport {
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
    private Boolean posQuickShiftControls;
    private String posLayout;
    private BigDecimal shiftMaxDifferenceAbsolute;
    private Long shiftMinOpenMinutes;
    private Long shiftMaxOpenHours;
    private Boolean shiftRequireSameDay;
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime updatedAt;
    private String updatedBy;
}
