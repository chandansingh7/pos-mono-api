package com.pos.config;

import com.pos.entity.*;
import com.pos.enums.OrderStatus;
import com.pos.enums.PaymentMethod;
import com.pos.enums.PaymentStatus;
import com.pos.enums.Role;
import com.pos.repository.*;
import com.pos.service.TaxRuleService;
import com.pos.entity.Company;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final UserRepository      userRepository;
    private final CategoryRepository  categoryRepository;
    private final ProductRepository   productRepository;
    private final InventoryRepository inventoryRepository;
    private final CustomerRepository  customerRepository;
    private final OrderRepository     orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository   paymentRepository;
    private final CompanyRepository   companyRepository;
    private final TaxRuleService      taxRuleService;
    private final PasswordEncoder     passwordEncoder;
    private final EntityManager       entityManager;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.info("=== DataInitializer: checking seed data ===");

        List<User>     users    = seedUsers();
        List<Category> cats     = seedCategories();
        List<Product>  products = seedProducts(cats);
        seedInventory(products);
        List<Customer> customers = seedCustomers();
        seedCompany();
        seedOrdersAndPayments(users, products, customers);
        taxRuleService.seedDefaults();

        log.info("=== DataInitializer: done ===");
    }

    // ── Users ────────────────────────────────────────────────────────────────

    private List<User> seedUsers() {
        if (userRepository.existsByUsername("admin")) {
            log.info("Users already seeded — skipping.");
            return userRepository.findAll();
        }
        List<User> users = userRepository.saveAll(List.of(
            user("admin",    "admin@pos.com",    "Admin@1234",   Role.ADMIN),
            user("manager1", "manager@pos.com",  "Manager@1234", Role.MANAGER),
            user("cashier1", "cashier1@pos.com", "Cashier@1234", Role.CASHIER),
            user("cashier2", "cashier2@pos.com", "Cashier@1234", Role.CASHIER)
        ));
        log.info("Seeded {} users", users.size());
        return users;
    }

    private User user(String username, String email, String rawPw, Role role) {
        return User.builder()
                .username(username)
                .email(email)
                .password(passwordEncoder.encode(rawPw))
                .role(role)
                .active(true)
                .build();
    }

    // ── Categories ───────────────────────────────────────────────────────────

    private List<Category> seedCategories() {
        if (categoryRepository.count() > 0) {
            log.info("Categories already seeded — skipping.");
            return categoryRepository.findAll();
        }
        List<Category> cats = categoryRepository.saveAll(List.of(
            cat("Electronics",    "Gadgets, phones, accessories"),
            cat("Food & Snacks",  "Packaged food, snacks and confectionery"),
            cat("Beverages",      "Water, juices, soft drinks and energy drinks"),
            cat("Clothing",       "T-shirts, jeans, jackets and accessories"),
            cat("Home & Kitchen", "Appliances, cookware and home essentials"),
            cat("Beauty & Care",  "Skincare, haircare and personal hygiene")
        ));
        log.info("Seeded {} categories", cats.size());
        return cats;
    }

    private Category cat(String name, String desc) {
        return Category.builder().name(name).description(desc).build();
    }

    // ── Company (single row) ─────────────────────────────────────────────────

    private void seedCompany() {
        if (companyRepository.count() > 0) {
            log.info("Company already seeded — skipping.");
            return;
        }
        Company company = Company.builder()
                .name("My Store")
                .address("123 Main Street")
                .phone("+1 234 567 8900")
                .email("store@example.com")
                .receiptPaperSize("80mm")
                .receiptFooterText("Thank you for your purchase!")
                .build();
        companyRepository.save(company);
        log.info("Seeded default company");
    }

    // ── Products ─────────────────────────────────────────────────────────────

    private List<Product> seedProducts(List<Category> cats) {
        if (productRepository.count() > 0) {
            log.info("Products already seeded — skipping.");
            return productRepository.findAll();
        }
        Category electronics = cats.get(0);
        Category food        = cats.get(1);
        Category beverages   = cats.get(2);
        Category clothing    = cats.get(3);
        Category home        = cats.get(4);
        Category beauty      = cats.get(5);

        List<Product> products = productRepository.saveAll(List.of(
            product("Wireless Earbuds Pro",       "SKU-E001", "4901234560011", new BigDecimal("49.99"),  electronics),
            product("USB-C Fast Charger 65W",     "SKU-E002", "4901234560028", new BigDecimal("19.99"),  electronics),
            product("Bluetooth Speaker Mini",     "SKU-E003", "4901234560035", new BigDecimal("34.99"),  electronics),
            product("Smartphone Screen Guard",    "SKU-E004", "4901234560042", new BigDecimal("8.99"),   electronics),
            product("Portable Power Bank 10K",    "SKU-E005", "4901234560059", new BigDecimal("29.99"),  electronics),
            product("Salted Potato Chips 200g",   "SKU-F001", "4901234561018", new BigDecimal("2.49"),   food),
            product("Dark Chocolate Bar 100g",    "SKU-F002", "4901234561025", new BigDecimal("3.99"),   food),
            product("Mixed Nuts 250g",            "SKU-F003", "4901234561032", new BigDecimal("6.99"),   food),
            product("Instant Noodles Pack",       "SKU-F004", "4901234561049", new BigDecimal("1.49"),   food),
            product("Organic Granola Bar",        "SKU-F005", "4901234561056", new BigDecimal("2.99"),   food),
            product("Mineral Water 500ml",        "SKU-B001", "4901234562015", new BigDecimal("0.99"),   beverages),
            product("Orange Juice 1L",            "SKU-B002", "4901234562022", new BigDecimal("3.49"),   beverages),
            product("Energy Drink 250ml",         "SKU-B003", "4901234562039", new BigDecimal("2.29"),   beverages),
            product("Green Tea 12-Pack",          "SKU-B004", "4901234562046", new BigDecimal("5.99"),   beverages),
            product("Cold Brew Coffee 330ml",     "SKU-B005", "4901234562053", new BigDecimal("4.49"),   beverages),
            product("Classic White T-Shirt M",    "SKU-C001", "4901234563012", new BigDecimal("14.99"),  clothing),
            product("Slim Fit Jeans 32x30",       "SKU-C002", "4901234563029", new BigDecimal("39.99"),  clothing),
            product("Sport Socks 3-Pack",         "SKU-C003", "4901234563036", new BigDecimal("9.99"),   clothing),
            product("Baseball Cap — Black",       "SKU-C004", "4901234563043", new BigDecimal("12.99"),  clothing),
            product("Stainless Steel Mug 350ml",  "SKU-H001", "4901234564019", new BigDecimal("11.99"),  home),
            product("Non-Stick Pan 24cm",         "SKU-H002", "4901234564026", new BigDecimal("22.99"),  home),
            product("Dish Soap 500ml",            "SKU-H003", "4901234564033", new BigDecimal("3.29"),   home),
            product("SPF 50 Sunscreen 100ml",     "SKU-P001", "4901234565016", new BigDecimal("8.99"),   beauty),
            product("Moisturising Shampoo 400ml", "SKU-P002", "4901234565023", new BigDecimal("7.49"),   beauty),
            product("Hand Sanitiser 250ml",       "SKU-P003", "4901234565030", new BigDecimal("4.99"),   beauty)
        ));
        log.info("Seeded {} products", products.size());
        return products;
    }

    private Product product(String name, String sku, String barcode, BigDecimal price, Category cat) {
        return Product.builder()
                .name(name).sku(sku).barcode(barcode)
                .price(price).category(cat).active(true)
                .build();
    }

    // ── Inventory ────────────────────────────────────────────────────────────

    private void seedInventory(List<Product> products) {
        if (inventoryRepository.count() > 0) {
            log.info("Inventory already seeded — skipping.");
            return;
        }
        int[] qtys       = {45, 80, 30, 120, 25, 200, 150, 90, 300, 180, 500, 100, 75, 60, 40, 35, 20, 60, 45, 70, 15, 250, 80, 110, 160};
        int[] thresholds = {10, 15, 10,  20,  5,  30,  25, 15,  50,  30,  50,  20, 10, 10, 10, 10,  5, 10, 10, 15,  5,  30, 15,  20,  25};

        for (int i = 0; i < products.size(); i++) {
            inventoryRepository.save(
                Inventory.builder()
                    .product(products.get(i))
                    .quantity(java.math.BigDecimal.valueOf(qtys[i]))
                    .lowStockThreshold(thresholds[i])
                    .build()
            );
        }
        log.info("Seeded inventory for {} products", products.size());
    }

    // ── Customers ────────────────────────────────────────────────────────────

    private List<Customer> seedCustomers() {
        if (customerRepository.count() > 0) {
            log.info("Customers already seeded — skipping.");
            return customerRepository.findAll();
        }
        List<Customer> customers = customerRepository.saveAll(List.of(
            customer("Alice Johnson",   "alice.johnson@email.com",   "+1-555-0101"),
            customer("Bob Martinez",    "bob.martinez@email.com",    "+1-555-0102"),
            customer("Carol Williams",  "carol.williams@email.com",  "+1-555-0103"),
            customer("David Lee",       "david.lee@email.com",       "+1-555-0104"),
            customer("Emma Thompson",   "emma.thompson@email.com",   "+1-555-0105"),
            customer("Frank Garcia",    "frank.garcia@email.com",    "+1-555-0106"),
            customer("Grace Chen",      "grace.chen@email.com",      "+1-555-0107"),
            customer("Henry Wilson",    "henry.wilson@email.com",    "+1-555-0108"),
            customer("Isabelle Moore",  "isabelle.moore@email.com",  "+1-555-0109"),
            customer("James Taylor",    "james.taylor@email.com",    "+1-555-0110"),
            customer("Karen Anderson",  "karen.anderson@email.com",  "+1-555-0111"),
            customer("Liam Jackson",    "liam.jackson@email.com",    "+1-555-0112")
        ));
        log.info("Seeded {} customers", customers.size());
        return customers;
    }

    private Customer customer(String name, String email, String phone) {
        return Customer.builder().name(name).email(email).phone(phone).build();
    }

    // ── Orders & Payments ────────────────────────────────────────────────────

    private void seedOrdersAndPayments(List<User> users, List<Product> products, List<Customer> customers) {
        if (orderRepository.count() > 0) {
            log.info("Orders already seeded — skipping.");
            return;
        }

        User cashier1 = users.stream().filter(u -> u.getUsername().equals("cashier1")).findFirst().orElse(users.get(0));
        User cashier2 = users.stream().filter(u -> u.getUsername().equals("cashier2")).findFirst().orElse(users.get(0));

        // Completed orders spread over the past 90 days
        createOrder(cashier1, customers.get(0),  products, new int[]{0, 5, 10},  new int[]{1, 3, 2},  OrderStatus.COMPLETED, PaymentMethod.CARD,  daysAgo(85));
        createOrder(cashier2, customers.get(1),  products, new int[]{15, 18},    new int[]{2, 1},     OrderStatus.COMPLETED, PaymentMethod.CASH,  daysAgo(80));
        createOrder(cashier1, customers.get(2),  products, new int[]{1, 3, 22},  new int[]{1, 2, 1},  OrderStatus.COMPLETED, PaymentMethod.CARD,  daysAgo(75));
        createOrder(cashier2, customers.get(3),  products, new int[]{6, 11, 14}, new int[]{2, 1, 3},  OrderStatus.COMPLETED, PaymentMethod.CASH,  daysAgo(70));
        createOrder(cashier1, customers.get(4),  products, new int[]{19, 20},    new int[]{1, 1},     OrderStatus.COMPLETED, PaymentMethod.CARD,  daysAgo(65));
        createOrder(cashier1, customers.get(5),  products, new int[]{0, 4, 12},  new int[]{1, 1, 4},  OrderStatus.COMPLETED, PaymentMethod.CASH,  daysAgo(60));
        createOrder(cashier2, customers.get(6),  products, new int[]{16, 17},    new int[]{1, 3},     OrderStatus.COMPLETED, PaymentMethod.CARD,  daysAgo(55));
        createOrder(cashier1, customers.get(7),  products, new int[]{7, 8, 9},   new int[]{2, 5, 3},  OrderStatus.COMPLETED, PaymentMethod.CASH,  daysAgo(50));
        createOrder(cashier2, customers.get(8),  products, new int[]{23, 24},    new int[]{2, 1},     OrderStatus.COMPLETED, PaymentMethod.CARD,  daysAgo(45));
        createOrder(cashier1, customers.get(9),  products, new int[]{2, 13},     new int[]{1, 2},     OrderStatus.COMPLETED, PaymentMethod.CARD,  daysAgo(40));
        createOrder(cashier2, customers.get(10), products, new int[]{5, 6, 21},  new int[]{4, 2, 1},  OrderStatus.COMPLETED, PaymentMethod.CASH,  daysAgo(35));
        createOrder(cashier1, customers.get(11), products, new int[]{0, 1},      new int[]{1, 1},     OrderStatus.COMPLETED, PaymentMethod.CARD,  daysAgo(30));
        createOrder(cashier2, customers.get(0),  products, new int[]{10, 11},    new int[]{6, 2},     OrderStatus.COMPLETED, PaymentMethod.CASH,  daysAgo(25));
        createOrder(cashier1, customers.get(2),  products, new int[]{3, 15, 22}, new int[]{3, 1, 1},  OrderStatus.COMPLETED, PaymentMethod.CARD,  daysAgo(20));
        createOrder(cashier2, customers.get(4),  products, new int[]{18, 19},    new int[]{2, 1},     OrderStatus.COMPLETED, PaymentMethod.CARD,  daysAgo(15));
        createOrder(cashier1, customers.get(6),  products, new int[]{4, 24},     new int[]{1, 2},     OrderStatus.COMPLETED, PaymentMethod.CASH,  daysAgo(12));
        createOrder(cashier2, customers.get(8),  products, new int[]{12, 13, 14},new int[]{3, 1, 2},  OrderStatus.COMPLETED, PaymentMethod.CARD,  daysAgo(10));
        createOrder(cashier1, customers.get(1),  products, new int[]{20, 21},    new int[]{1, 1},     OrderStatus.COMPLETED, PaymentMethod.CASH,  daysAgo(7));
        createOrder(cashier2, customers.get(3),  products, new int[]{0, 16},     new int[]{1, 2},     OrderStatus.COMPLETED, PaymentMethod.CARD,  daysAgo(5));
        createOrder(cashier1, customers.get(5),  products, new int[]{9, 23},     new int[]{3, 1},     OrderStatus.COMPLETED, PaymentMethod.CASH,  daysAgo(4));
        // A few cancelled and pending for realistic variety
        createOrder(cashier2, customers.get(7),  products, new int[]{1, 6},      new int[]{1, 2},     OrderStatus.CANCELLED, PaymentMethod.CARD,  daysAgo(20));
        createOrder(cashier1, null,               products, new int[]{10, 11},    new int[]{2, 1},     OrderStatus.PENDING,   PaymentMethod.CASH,  daysAgo(1));

        log.info("Seeded orders with order items and payments");
    }

    private void createOrder(User cashier, Customer customer, List<Product> products,
                             int[] productIdxs, int[] quantities,
                             OrderStatus status, PaymentMethod paymentMethod,
                             LocalDateTime createdAt) {

        BigDecimal subtotal = BigDecimal.ZERO;
        for (int i = 0; i < productIdxs.length; i++) {
            BigDecimal lineTotal = products.get(productIdxs[i]).getPrice()
                    .multiply(BigDecimal.valueOf(quantities[i]));
            subtotal = subtotal.add(lineTotal);
        }
        BigDecimal tax      = subtotal.multiply(new BigDecimal("0.08")).setScale(2, java.math.RoundingMode.HALF_UP);
        BigDecimal discount = BigDecimal.ZERO;
        BigDecimal total    = subtotal.add(tax).subtract(discount);

        Order order = Order.builder()
                .cashier(cashier)
                .customer(customer)
                .subtotal(subtotal)
                .tax(tax)
                .discount(discount)
                .total(total)
                .status(status)
                .paymentMethod(paymentMethod)
                .build();
        order = orderRepository.save(order);
        orderRepository.flush();

        // Backdate created_at via native SQL since the column is marked updatable = false
        entityManager.createNativeQuery(
                "UPDATE orders SET created_at = :ts WHERE id = :id")
                .setParameter("ts", createdAt)
                .setParameter("id", order.getId())
                .executeUpdate();
        entityManager.refresh(order);

        for (int i = 0; i < productIdxs.length; i++) {
            Product p        = products.get(productIdxs[i]);
            BigDecimal qty   = BigDecimal.valueOf(quantities[i]);
            BigDecimal unitP = p.getPrice();
            orderItemRepository.save(
                OrderItem.builder()
                    .order(order)
                    .product(p)
                    .quantity(qty)
                    .unitPrice(unitP)
                    .subtotal(unitP.multiply(qty))
                    .build()
            );
        }

        if (status == OrderStatus.COMPLETED) {
            paymentRepository.save(
                Payment.builder()
                    .order(order)
                    .method(paymentMethod)
                    .amount(total)
                    .status(PaymentStatus.COMPLETED)
                    .transactionId("TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                    .build()
            );
        }
    }

    private LocalDateTime daysAgo(int days) {
        return LocalDateTime.now().minusDays(days).withHour(10).withMinute(30).withSecond(0);
    }
}
