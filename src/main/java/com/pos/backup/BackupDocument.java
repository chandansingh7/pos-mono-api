package com.pos.backup;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Root structure for JSON backup format. Version and exportedAt allow
 * schema evolution and audit.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BackupDocument {

    public static final int VERSION = 1;

    private int version;
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime exportedAt;

    private CompanyExport company;
    private List<UserExport> users;
    private List<CategoryExport> categories;
    private List<ProductExport> products;
    private List<CustomerExport> customers;
    private List<OrderExport> orders;
    private List<OrderItemExport> orderItems;
    private List<PaymentExport> payments;
    private List<ShiftExport> shifts;
    private List<InventoryExport> inventory;
    private List<LabelExport> labels;
    private List<AccessLogExport> accessLogs;
    private List<UserAllowedIpExport> userAllowedIps;
    private List<UserBlockedIpExport> userBlockedIps;
}
