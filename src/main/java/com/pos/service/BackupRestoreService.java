package com.pos.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.pos.backup.*;
import com.pos.config.BackupConfig;
import com.pos.entity.*;
import com.pos.exception.BadRequestException;
import com.pos.exception.ErrorCode;
import com.pos.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BackupRestoreService {

    private final BackupConfig backupConfig;
    private final DataSource dataSource;
    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;
    private final ShiftRepository shiftRepository;
    private final InventoryRepository inventoryRepository;
    private final LabelRepository labelRepository;
    private final AccessLogRepository accessLogRepository;
    private final UserAllowedIpRepository userAllowedIpRepository;
    private final UserBlockedIpRepository userBlockedIpRepository;

    @Value("${spring.datasource.url:}")
    private String datasourceUrl;

    @Value("${spring.datasource.username:}")
    private String datasourceUsername;

    @Value("${spring.datasource.password:}")
    private String datasourcePassword;

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    // ─── JSON Export ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public byte[] exportToJson() throws IOException {
        BackupDocument doc = buildBackupDocument();
        return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(doc);
    }

    private BackupDocument buildBackupDocument() {
        List<Company> companies = companyRepository.findAll();
        Company c = companies.isEmpty() ? null : companies.get(0);

        return BackupDocument.builder()
                .version(BackupDocument.VERSION)
                .exportedAt(LocalDateTime.now())
                .company(c == null ? null : toCompanyExport(c))
                .users(userRepository.findAll().stream().map(this::toUserExport).collect(Collectors.toList()))
                .categories(categoryRepository.findAll().stream().map(this::toCategoryExport).collect(Collectors.toList()))
                .products(productRepository.findAll().stream().map(this::toProductExport).collect(Collectors.toList()))
                .customers(customerRepository.findAll().stream().map(this::toCustomerExport).collect(Collectors.toList()))
                .orders(orderRepository.findAll().stream().map(this::toOrderExport).collect(Collectors.toList()))
                .orderItems(orderItemRepository.findAll().stream().map(this::toOrderItemExport).collect(Collectors.toList()))
                .payments(paymentRepository.findAll().stream().map(this::toPaymentExport).collect(Collectors.toList()))
                .shifts(shiftRepository.findAll().stream().map(this::toShiftExport).collect(Collectors.toList()))
                .inventory(inventoryRepository.findAll().stream().map(this::toInventoryExport).collect(Collectors.toList()))
                .labels(labelRepository.findAll().stream().map(this::toLabelExport).collect(Collectors.toList()))
                .accessLogs(accessLogRepository.findAll().stream().map(this::toAccessLogExport).collect(Collectors.toList()))
                .userAllowedIps(userAllowedIpRepository.findAll().stream().map(this::toUserAllowedIpExport).collect(Collectors.toList()))
                .userBlockedIps(userBlockedIpRepository.findAll().stream().map(this::toUserBlockedIpExport).collect(Collectors.toList()))
                .build();
    }

    private CompanyExport toCompanyExport(Company c) {
        return CompanyExport.builder()
                .id(c.getId()).name(c.getName()).logoUrl(c.getLogoUrl()).faviconUrl(c.getFaviconUrl())
                .address(c.getAddress()).phone(c.getPhone()).email(c.getEmail()).taxId(c.getTaxId()).website(c.getWebsite())
                .receiptFooterText(c.getReceiptFooterText()).receiptPaperSize(c.getReceiptPaperSize()).receiptHeaderText(c.getReceiptHeaderText())
                .displayCurrency(c.getDisplayCurrency()).locale(c.getLocale()).posQuickShiftControls(c.getPosQuickShiftControls())
                .shiftMaxDifferenceAbsolute(c.getShiftMaxDifferenceAbsolute()).shiftMinOpenMinutes(c.getShiftMinOpenMinutes())
                .shiftMaxOpenHours(c.getShiftMaxOpenHours()).shiftRequireSameDay(c.getShiftRequireSameDay())
                .updatedAt(c.getUpdatedAt()).updatedBy(c.getUpdatedBy())
                .build();
    }

    private UserExport toUserExport(User u) {
        return UserExport.builder()
                .id(u.getId()).username(u.getUsername()).email(u.getEmail()).password(u.getPassword())
                .role(u.getRole() != null ? u.getRole().name() : null).active(u.isActive())
                .firstName(u.getFirstName()).lastName(u.getLastName()).phone(u.getPhone()).address(u.getAddress()).deliveryAddress(u.getDeliveryAddress())
                .build();
    }

    private CategoryExport toCategoryExport(Category c) {
        return CategoryExport.builder()
                .id(c.getId()).name(c.getName()).description(c.getDescription()).updatedAt(c.getUpdatedAt()).updatedBy(c.getUpdatedBy())
                .build();
    }

    private ProductExport toProductExport(Product p) {
        return ProductExport.builder()
                .id(p.getId()).name(p.getName()).sku(p.getSku()).barcode(p.getBarcode()).size(p.getSize()).color(p.getColor())
                .price(p.getPrice()).categoryId(p.getCategory() != null ? p.getCategory().getId() : null).imageUrl(p.getImageUrl())
                .active(p.isActive()).createdAt(p.getCreatedAt()).updatedAt(p.getUpdatedAt())
                .build();
    }

    private CustomerExport toCustomerExport(Customer c) {
        return CustomerExport.builder()
                .id(c.getId()).name(c.getName()).email(c.getEmail()).phone(c.getPhone()).rewardPoints(c.getRewardPoints())
                .memberCardBarcode(c.getMemberCardBarcode()).createdAt(c.getCreatedAt()).updatedAt(c.getUpdatedAt()).updatedBy(c.getUpdatedBy())
                .build();
    }

    private OrderExport toOrderExport(Order o) {
        return OrderExport.builder()
                .id(o.getId())
                .customerId(o.getCustomer() != null ? o.getCustomer().getId() : null)
                .cashierId(o.getCashier() != null ? o.getCashier().getId() : null)
                .subtotal(o.getSubtotal()).tax(o.getTax()).discount(o.getDiscount()).total(o.getTotal())
                .status(o.getStatus() != null ? o.getStatus().name() : null)
                .paymentMethod(o.getPaymentMethod() != null ? o.getPaymentMethod().name() : null)
                .createdAt(o.getCreatedAt())
                .build();
    }

    private OrderItemExport toOrderItemExport(OrderItem oi) {
        return OrderItemExport.builder()
                .id(oi.getId()).orderId(oi.getOrder().getId()).productId(oi.getProduct().getId())
                .quantity(oi.getQuantity()).unitPrice(oi.getUnitPrice()).subtotal(oi.getSubtotal())
                .build();
    }

    private PaymentExport toPaymentExport(Payment p) {
        return PaymentExport.builder()
                .id(p.getId()).orderId(p.getOrder().getId()).method(p.getMethod().name()).amount(p.getAmount())
                .status(p.getStatus().name()).transactionId(p.getTransactionId()).createdAt(p.getCreatedAt())
                .build();
    }

    private ShiftExport toShiftExport(Shift s) {
        return ShiftExport.builder()
                .id(s.getId()).cashierId(s.getCashier().getId()).openingFloat(s.getOpeningFloat())
                .cashSales(s.getCashSales()).expectedCash(s.getExpectedCash()).countedCash(s.getCountedCash()).difference(s.getDifference())
                .status(s.getStatus().name()).openedAt(s.getOpenedAt()).closedAt(s.getClosedAt())
                .build();
    }

    private InventoryExport toInventoryExport(Inventory i) {
        return InventoryExport.builder()
                .id(i.getId()).productId(i.getProduct().getId()).quantity(i.getQuantity()).lowStockThreshold(i.getLowStockThreshold())
                .updatedAt(i.getUpdatedAt()).updatedBy(i.getUpdatedBy())
                .build();
    }

    private LabelExport toLabelExport(Label l) {
        return LabelExport.builder()
                .id(l.getId()).barcode(l.getBarcode()).name(l.getName()).price(l.getPrice()).sku(l.getSku())
                .categoryId(l.getCategory() != null ? l.getCategory().getId() : null)
                .productId(l.getProduct() != null ? l.getProduct().getId() : null)
                .createdAt(l.getCreatedAt())
                .build();
    }

    private AccessLogExport toAccessLogExport(AccessLog a) {
        return AccessLogExport.builder()
                .id(a.getId()).username(a.getUsername()).ipAddress(a.getIpAddress()).country(a.getCountry())
                .userAgent(a.getUserAgent()).path(a.getPath()).action(a.getAction()).createdAt(a.getCreatedAt())
                .build();
    }

    private UserAllowedIpExport toUserAllowedIpExport(UserAllowedIp u) {
        return UserAllowedIpExport.builder()
                .id(u.getId()).userId(u.getUser().getId()).ipAddress(u.getIpAddress()).createdAt(u.getCreatedAt())
                .build();
    }

    private UserBlockedIpExport toUserBlockedIpExport(UserBlockedIp u) {
        return UserBlockedIpExport.builder()
                .id(u.getId()).userId(u.getUser().getId()).ipAddress(u.getIpAddress()).createdAt(u.getCreatedAt())
                .build();
    }

    // ─── JSON Restore ──────────────────────────────────────────────────────────

    @Transactional
    public void restoreFromJson(byte[] data) throws IOException {
        BackupDocument doc = MAPPER.readValue(data, BackupDocument.class);
        if (doc.getVersion() != BackupDocument.VERSION) {
            throw new BadRequestException(ErrorCode.BR001, "Backup version " + doc.getVersion() + " not supported. Expected " + BackupDocument.VERSION);
        }
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        deleteAllInOrder(jdbc);
        insertAllInOrder(jdbc, doc);
    }

    private void deleteAllInOrder(JdbcTemplate jdbc) {
        String[] tables = {"order_items", "payments", "orders", "labels", "inventory", "products", "shifts", "access_log", "user_allowed_ip", "user_blocked_ip", "users", "categories", "customers", "company"};
        for (String table : tables) {
            try {
                jdbc.execute("DELETE FROM " + table);
            } catch (Exception e) {
                log.warn("Delete from {}: {}", table, e.getMessage());
            }
        }
    }

    private void insertAllInOrder(JdbcTemplate jdbc, BackupDocument doc) {
        if (doc.getCompany() != null) {
            CompanyExport c = doc.getCompany();
            jdbc.update("INSERT INTO company (id,name,logo_url,favicon_url,address,phone,email,tax_id,website,receipt_footer_text,receipt_paper_size,receipt_header_text,display_currency,locale,pos_quick_shift_controls,shift_max_difference_abs,shift_min_open_minutes,shift_max_open_hours,shift_require_same_day,updated_at,updated_by) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    c.getId(), c.getName(), c.getLogoUrl(), c.getFaviconUrl(), c.getAddress(), c.getPhone(), c.getEmail(), c.getTaxId(), c.getWebsite(),
                    c.getReceiptFooterText(), c.getReceiptPaperSize(), c.getReceiptHeaderText(), c.getDisplayCurrency(), c.getLocale(), c.getPosQuickShiftControls(),
                    c.getShiftMaxDifferenceAbsolute(), c.getShiftMinOpenMinutes(), c.getShiftMaxOpenHours(), c.getShiftRequireSameDay(), c.getUpdatedAt(), c.getUpdatedBy());
        }
        if (doc.getCategories() != null) for (CategoryExport e : doc.getCategories()) {
            jdbc.update("INSERT INTO categories (id,name,description,updated_at,updated_by) VALUES (?,?,?,?,?)",
                    e.getId(), e.getName(), e.getDescription(), e.getUpdatedAt(), e.getUpdatedBy());
        }
        if (doc.getUsers() != null) for (UserExport e : doc.getUsers()) {
            jdbc.update("INSERT INTO users (id,username,email,password,role,active,first_name,last_name,phone,address,delivery_address) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                    e.getId(), e.getUsername(), e.getEmail(), e.getPassword(), e.getRole(), e.isActive(), e.getFirstName(), e.getLastName(), e.getPhone(), e.getAddress(), e.getDeliveryAddress());
        }
        if (doc.getCustomers() != null) for (CustomerExport e : doc.getCustomers()) {
            jdbc.update("INSERT INTO customers (id,name,email,phone,reward_points,member_card_barcode,created_at,updated_at,updated_by) VALUES (?,?,?,?,?,?,?,?,?)",
                    e.getId(), e.getName(), e.getEmail(), e.getPhone(), e.getRewardPoints() != null ? e.getRewardPoints() : 0, e.getMemberCardBarcode(), e.getCreatedAt(), e.getUpdatedAt(), e.getUpdatedBy());
        }
        if (doc.getProducts() != null) for (ProductExport e : doc.getProducts()) {
            jdbc.update("INSERT INTO products (id,name,sku,barcode,size,color,price,category_id,image_url,active,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                    e.getId(), e.getName(), e.getSku(), e.getBarcode(), e.getSize(), e.getColor(), e.getPrice(), e.getCategoryId(), e.getImageUrl(), e.isActive(), e.getCreatedAt(), e.getUpdatedAt());
        }
        if (doc.getOrders() != null) for (OrderExport e : doc.getOrders()) {
            jdbc.update("INSERT INTO orders (id,customer_id,cashier_id,subtotal,tax,discount,total,status,payment_method,created_at) VALUES (?,?,?,?,?,?,?,?,?,?)",
                    e.getId(), e.getCustomerId(), e.getCashierId(), e.getSubtotal(), e.getTax(), e.getDiscount(), e.getTotal(), e.getStatus(), e.getPaymentMethod(), e.getCreatedAt());
        }
        if (doc.getOrderItems() != null) for (OrderItemExport e : doc.getOrderItems()) {
            jdbc.update("INSERT INTO order_items (id,order_id,product_id,quantity,unit_price,subtotal) VALUES (?,?,?,?,?,?)",
                    e.getId(), e.getOrderId(), e.getProductId(), e.getQuantity(), e.getUnitPrice(), e.getSubtotal());
        }
        if (doc.getPayments() != null) for (PaymentExport e : doc.getPayments()) {
            jdbc.update("INSERT INTO payments (id,order_id,method,amount,status,transaction_id,created_at) VALUES (?,?,?,?,?,?,?)",
                    e.getId(), e.getOrderId(), e.getMethod(), e.getAmount(), e.getStatus(), e.getTransactionId(), e.getCreatedAt());
        }
        if (doc.getShifts() != null) for (ShiftExport e : doc.getShifts()) {
            jdbc.update("INSERT INTO shifts (id,cashier_id,opening_float,cash_sales,expected_cash,counted_cash,difference,status,opened_at,closed_at) VALUES (?,?,?,?,?,?,?,?,?,?)",
                    e.getId(), e.getCashierId(), e.getOpeningFloat(), e.getCashSales(), e.getExpectedCash(), e.getCountedCash(), e.getDifference(), e.getStatus(), e.getOpenedAt(), e.getClosedAt());
        }
        if (doc.getInventory() != null) for (InventoryExport e : doc.getInventory()) {
            jdbc.update("INSERT INTO inventory (id,product_id,quantity,low_stock_threshold,updated_at,updated_by) VALUES (?,?,?,?,?,?)",
                    e.getId(), e.getProductId(), e.getQuantity(), e.getLowStockThreshold(), e.getUpdatedAt(), e.getUpdatedBy());
        }
        if (doc.getLabels() != null) for (LabelExport e : doc.getLabels()) {
            jdbc.update("INSERT INTO labels (id,barcode,name,price,sku,category_id,product_id,created_at) VALUES (?,?,?,?,?,?,?,?)",
                    e.getId(), e.getBarcode(), e.getName(), e.getPrice(), e.getSku(), e.getCategoryId(), e.getProductId(), e.getCreatedAt());
        }
        if (doc.getAccessLogs() != null) for (AccessLogExport e : doc.getAccessLogs()) {
            jdbc.update("INSERT INTO access_log (id,username,ip_address,country,user_agent,path,action,created_at) VALUES (?,?,?,?,?,?,?,?)",
                    e.getId(), e.getUsername(), e.getIpAddress(), e.getCountry(), e.getUserAgent(), e.getPath(), e.getAction(), e.getCreatedAt());
        }
        if (doc.getUserAllowedIps() != null) for (UserAllowedIpExport e : doc.getUserAllowedIps()) {
            jdbc.update("INSERT INTO user_allowed_ip (id,user_id,ip_address,created_at) VALUES (?,?,?,?)",
                    e.getId(), e.getUserId(), e.getIpAddress(), e.getCreatedAt());
        }
        if (doc.getUserBlockedIps() != null) for (UserBlockedIpExport e : doc.getUserBlockedIps()) {
            jdbc.update("INSERT INTO user_blocked_ip (id,user_id,ip_address,created_at) VALUES (?,?,?,?)",
                    e.getId(), e.getUserId(), e.getIpAddress(), e.getCreatedAt());
        }
        resetSequences(jdbc);
    }

    private void resetSequences(JdbcTemplate jdbc) {
        String[][] seqTable = {
                {"company_id_seq", "company"}, {"categories_id_seq", "categories"}, {"users_id_seq", "users"},
                {"customers_id_seq", "customers"}, {"products_id_seq", "products"}, {"orders_id_seq", "orders"},
                {"order_items_id_seq", "order_items"}, {"payments_id_seq", "payments"}, {"shifts_id_seq", "shifts"},
                {"inventory_id_seq", "inventory"}, {"labels_id_seq", "labels"}, {"access_log_id_seq", "access_log"},
                {"user_allowed_ip_id_seq", "user_allowed_ip"}, {"user_blocked_ip_id_seq", "user_blocked_ip"}
        };
        for (String[] st : seqTable) {
            try {
                jdbc.execute("SELECT setval('" + st[0] + "', COALESCE((SELECT MAX(id) FROM " + st[1] + "), 1))");
            } catch (Exception e) {
                log.debug("Reset sequence {}: {}", st[0], e.getMessage());
            }
        }
    }

    // ─── SQL Export (pg_dump) ─────────────────────────────────────────────────

    public byte[] exportToSql() throws IOException {
        if (!backupConfig.isSqlBackupEnabled()) {
            throw new BadRequestException(ErrorCode.BR001, "SQL backup is disabled");
        }
        if (datasourceUrl == null || !datasourceUrl.startsWith("jdbc:postgresql:")) {
            throw new BadRequestException(ErrorCode.BR001, "SQL backup requires PostgreSQL");
        }
        String[] conn = parsePostgresUrl(datasourceUrl);
        String host = conn[0];
        String port = conn[1];
        String dbname = conn[2];
        ProcessBuilder pb = new ProcessBuilder(
                backupConfig.getPgDumpPath(),
                "-h", host, "-p", port, "-U", datasourceUsername, "-d", dbname,
                "--no-owner", "--no-acl", "-F", "p"
        );
        pb.redirectErrorStream(true);
        Map<String, String> env = pb.environment();
        if (datasourcePassword != null && !datasourcePassword.isEmpty()) {
            env.put("PGPASSWORD", datasourcePassword);
        }
        Process p = pb.start();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InputStream in = p.getInputStream()) {
            in.transferTo(out);
        }
        try {
            int exit = p.waitFor();
            if (exit != 0) throw new BadRequestException(ErrorCode.BR001, "pg_dump failed: " + out.toString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BadRequestException(ErrorCode.BR001, "Backup interrupted");
        }
        return out.toByteArray();
    }

    private String[] parsePostgresUrl(String url) {
        Pattern p = Pattern.compile("jdbc:postgresql://([^:/]+):?(\\d*)/([^?]*)");
        Matcher m = p.matcher(url);
        if (m.find()) {
            String db = m.group(3);
            if (db == null || db.isEmpty()) db = "postgres";
            return new String[]{m.group(1), m.group(2) == null || m.group(2).isEmpty() ? "5432" : m.group(2), db};
        }
        return new String[]{"localhost", "5432", "postgres"};
    }

    // ─── SQL Restore (psql) ───────────────────────────────────────────────────

    public void restoreFromSql(byte[] data) throws IOException {
        if (!backupConfig.isSqlBackupEnabled()) {
            throw new BadRequestException(ErrorCode.BR001, "SQL restore is disabled");
        }
        if (datasourceUrl == null || !datasourceUrl.startsWith("jdbc:postgresql:")) {
            throw new BadRequestException(ErrorCode.BR001, "SQL restore requires PostgreSQL");
        }
        String[] conn = parsePostgresUrl(datasourceUrl);
        Path tmp = Files.createTempFile("pos-restore-", ".sql");
        try {
            Files.write(tmp, data);
            ProcessBuilder pb = new ProcessBuilder(
                    backupConfig.getPsqlPath(),
                    "-h", conn[0], "-p", conn[1], "-U", datasourceUsername, "-d", conn[2],
                    "-f", tmp.toAbsolutePath().toString()
            );
            pb.redirectErrorStream(true);
            Map<String, String> env = pb.environment();
            if (datasourcePassword != null && !datasourcePassword.isEmpty()) {
                env.put("PGPASSWORD", datasourcePassword);
            }
            Process p = pb.start();
            ByteArrayOutputStream err = new ByteArrayOutputStream();
            try (InputStream in = p.getInputStream()) {
                in.transferTo(err);
            }
            int exit = p.waitFor();
            if (exit != 0) throw new BadRequestException(ErrorCode.BR001, "psql restore failed: " + err.toString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BadRequestException(ErrorCode.BR001, "Restore interrupted");
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    public boolean isSqlBackupAvailable() {
        return backupConfig.isSqlBackupEnabled() && datasourceUrl != null && datasourceUrl.startsWith("jdbc:postgresql:");
    }
}
