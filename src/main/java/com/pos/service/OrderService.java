package com.pos.service;

import com.pos.config.RewardConfig;
import com.pos.dto.request.OrderItemRequest;
import com.pos.dto.request.OrderRequest;
import com.pos.dto.request.RefundRequest;
import com.pos.dto.response.OrderResponse;
import com.pos.entity.*;
import com.pos.entity.RefundItem;
import com.pos.enums.OrderStatus;
import com.pos.enums.PaymentStatus;
import com.pos.exception.BadRequestException;
import com.pos.exception.ErrorCode;
import com.pos.exception.ResourceNotFoundException;
import com.pos.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    /** Backward-compatible default — used when company.taxRate is null. */
    private static final BigDecimal DEFAULT_TAX_RATE = new BigDecimal("0.10");

    private final OrderRepository      orderRepository;
    private final OrderItemRepository  orderItemRepository;
    private final ProductRepository    productRepository;
    private final InventoryRepository  inventoryRepository;
    private final CustomerRepository   customerRepository;
    private final UserRepository       userRepository;
    private final PaymentRepository    paymentRepository;
    private final CompanyRepository    companyRepository;
    private final RefundRepository     refundRepository;
    private final RefundItemRepository refundItemRepository;
    private final TaxRuleRepository    taxRuleRepository;
    private final RewardConfig         rewardConfig;
    private final ReceiptEmailService  receiptEmailService;

    public Page<OrderResponse> getAll(Pageable pageable) {
        log.debug("Fetching orders — page: {}", pageable.getPageNumber());
        return orderRepository.findAll(pageable).map(this::buildOrderResponse);
    }

    public OrderResponse getById(Long id) {
        log.debug("Fetching order id: {}", id);
        Order order = findById(id);
        return buildOrderResponse(order);
    }

    private OrderResponse buildOrderResponse(Order order) {
        Long orderId = order.getId();
        List<Refund> refunds = refundRepository.findAllByOrderIdOrderByRefundedAtDesc(orderId);
        BigDecimal refundedAmount = refundRepository.sumAmountByOrderId(orderId);
        Map<Long, BigDecimal> refundedQtyByItemId = new HashMap<>();
        for (RefundItem ri : refundItemRepository.findAllByRefundOrderId(orderId)) {
            Long itemId = ri.getOrderItem().getId();
            refundedQtyByItemId.merge(itemId, ri.getQuantity(), BigDecimal::add);
        }
        return OrderResponse.from(order, refunds, refundedAmount, refundedQtyByItemId);
    }

    @Transactional
    public OrderResponse create(OrderRequest request) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        log.info("Creating order — cashier: '{}', items: {}", username,
                request.getItems() != null ? request.getItems().size() : 0);

        User cashier = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.US001));

        Customer customer = null;
        if (request.getCustomerId() != null) {
            customer = customerRepository.findById(request.getCustomerId())
                    .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CM001));
        }

        BigDecimal discount = request.getDiscount() != null ? request.getDiscount() : BigDecimal.ZERO;
        Integer pointsToRedeem = request.getPointsToRedeem();
        if (customer != null && pointsToRedeem != null && pointsToRedeem > 0) {
            int balance = customer.getRewardPoints() != null ? customer.getRewardPoints() : 0;
            if (balance < pointsToRedeem) {
                log.warn("[RW001] Insufficient reward points: has {}, requested {}", balance, pointsToRedeem);
                throw new BadRequestException(ErrorCode.RW001);
            }
            int rate = rewardConfig.getRedemptionRate();
            if (rate <= 0) rate = 100;
            BigDecimal redemptionDollars = BigDecimal.valueOf(pointsToRedeem)
                    .divide(BigDecimal.valueOf(rate), 2, RoundingMode.DOWN);
            discount = discount.add(redemptionDollars);
            customer.setRewardPoints(balance - pointsToRedeem);
            customerRepository.save(customer);
        }

        // Pre-load tax rules for per-product resolution
        Map<String, BigDecimal> taxRuleMap = taxRuleRepository.findAllByOrderByTaxCategoryAsc()
                .stream().collect(Collectors.toMap(TaxRule::getTaxCategory, TaxRule::getRate));

        // Load company for global tax settings
        Company company = companyRepository.findFirstByOrderByIdAsc().orElse(null);
        boolean taxEnabled = company == null || !Boolean.FALSE.equals(company.getTaxEnabled());
        BigDecimal globalTaxRate = (company != null && company.getTaxRate() != null)
                ? company.getTaxRate() : DEFAULT_TAX_RATE;

        List<OrderItem> items             = new ArrayList<>();
        List<Inventory> inventoriesToSave = new ArrayList<>();
        BigDecimal subtotal               = BigDecimal.ZERO;
        BigDecimal totalTax               = BigDecimal.ZERO;

        for (OrderItemRequest itemReq : request.getItems()) {
            Product product = productRepository.findById(itemReq.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.PR001));

            if (!product.isActive()) {
                log.warn("[PR004] Order rejected — product not available: '{}'", product.getName());
                throw new BadRequestException(ErrorCode.PR004, product.getName());
            }

            Inventory inventory = inventoryRepository.findByProductId(product.getId())
                    .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.IN001));

            if (inventory.getQuantity().compareTo(itemReq.getQuantity()) < 0) {
                log.warn("[OR002] Insufficient stock for '{}': available {}, requested {}",
                        product.getName(), inventory.getQuantity(), itemReq.getQuantity());
                throw new BadRequestException(ErrorCode.OR002,
                        product.getName() + " — available: " + inventory.getQuantity());
            }

            BigDecimal itemSubtotal = product.getPrice().multiply(itemReq.getQuantity());
            items.add(OrderItem.builder()
                    .product(product).quantity(itemReq.getQuantity())
                    .unitPrice(product.getPrice()).subtotal(itemSubtotal).build());

            subtotal = subtotal.add(itemSubtotal);
            inventory.setQuantity(inventory.getQuantity().subtract(itemReq.getQuantity()));
            inventoriesToSave.add(inventory);

            // Per-item tax contribution (used for per-product tax in totalTax)
            if (taxEnabled) {
                BigDecimal itemRate = resolveItemTaxRate(product, taxRuleMap, globalTaxRate);
                totalTax = totalTax.add(itemSubtotal.multiply(itemRate));
            }
        }

        inventoryRepository.saveAll(inventoriesToSave);

        BigDecimal afterDiscount = subtotal.subtract(discount).max(BigDecimal.ZERO);

        // Adjust tax proportionally when discount reduces the taxable base
        BigDecimal tax;
        if (taxEnabled && subtotal.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal discountRatio = afterDiscount.divide(subtotal, 10, RoundingMode.HALF_UP);
            tax = totalTax.multiply(discountRatio).setScale(2, RoundingMode.HALF_UP);
        } else {
            tax = BigDecimal.ZERO;
        }

        BigDecimal total = afterDiscount.add(tax).setScale(2, RoundingMode.HALF_UP);

        Order order = orderRepository.save(Order.builder()
                .customer(customer).cashier(cashier)
                .subtotal(subtotal).tax(tax).discount(discount).total(total)
                .status(OrderStatus.COMPLETED)
                .paymentMethod(request.getPaymentMethod())
                .build());

        for (OrderItem item : items) item.setOrder(order);
        orderItemRepository.saveAll(items);
        order.setItems(items);

        paymentRepository.save(Payment.builder()
                .order(order).method(request.getPaymentMethod())
                .amount(total).status(PaymentStatus.COMPLETED).build());

        if (customer != null) {
            int pointsPerDollar = rewardConfig.getPointsPerDollar();
            if (pointsPerDollar > 0) {
                int earned = subtotal.multiply(BigDecimal.valueOf(pointsPerDollar)).intValue();
                if (earned > 0) {
                    int current = customer.getRewardPoints() != null ? customer.getRewardPoints() : 0;
                    customer.setRewardPoints(current + earned);
                    customerRepository.save(customer);
                    log.info("Order id: {} — customer {} earned {} reward points",
                            order.getId(), customer.getId(), earned);
                }
            }
        }

        log.info("Order created — id: {}, subtotal: {}, tax: {}, total: {}", order.getId(), subtotal, tax, total);
        return buildOrderResponse(order);
    }

    @Transactional
    public OrderResponse cancel(Long id) {
        log.info("Cancelling order id: {}", id);
        Order order = findById(id);

        if (order.getStatus() == OrderStatus.CANCELLED) {
            log.warn("[OR003] Cancel rejected — order id: {} already cancelled", id);
            throw new BadRequestException(ErrorCode.OR003);
        }
        if (order.getStatus() == OrderStatus.REFUNDED || order.getStatus() == OrderStatus.PARTIALLY_REFUNDED) {
            log.warn("[OR004] Cancel rejected — order id: {} is refunded or partially refunded", id);
            throw new BadRequestException(ErrorCode.OR004);
        }

        restoreInventory(order);

        order.setStatus(OrderStatus.CANCELLED);
        paymentRepository.findByOrderId(id).ifPresent(p -> {
            p.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(p);
        });

        log.info("Order id: {} cancelled", id);
        return buildOrderResponse(orderRepository.save(order));
    }

    @Transactional
    public OrderResponse refund(Long id, RefundRequest request) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        log.info("Refunding order id: {} by '{}' (partial={})", id, username,
                request != null && request.getItems() != null && !request.getItems().isEmpty());

        Order order = findById(id);

        if (order.getStatus() == OrderStatus.REFUNDED) {
            log.warn("[OR006] Refund rejected — order id: {} already refunded", id);
            throw new BadRequestException(ErrorCode.OR006);
        }
        if (order.getStatus() != OrderStatus.COMPLETED && order.getStatus() != OrderStatus.PARTIALLY_REFUNDED) {
            log.warn("[OR007] Refund rejected — order id: {} has status {}", id, order.getStatus());
            throw new BadRequestException(ErrorCode.OR007);
        }

        boolean isPartial = request != null && request.getItems() != null && !request.getItems().isEmpty();
        BigDecimal refundAmount;
        List<RefundItem> refundItems = new ArrayList<>();

        if (isPartial) {
            // Partial refund: validate and build refund items
            BigDecimal existingRefunded = refundRepository.sumAmountByOrderId(id);
            Map<Long, BigDecimal> alreadyRefundedQty = new HashMap<>();
            for (RefundItem ri : refundItemRepository.findAllByRefundOrderId(id)) {
                Long itemId = ri.getOrderItem().getId();
                alreadyRefundedQty.merge(itemId, ri.getQuantity(), BigDecimal::add);
            }

            BigDecimal partialSubtotal = BigDecimal.ZERO;
            for (com.pos.dto.request.RefundItemRequest itemReq : request.getItems()) {
                OrderItem oi = order.getItems().stream()
                        .filter(i -> i.getId().equals(itemReq.getOrderItemId()))
                        .findFirst()
                        .orElse(null);
                if (oi == null) {
                    log.warn("[OR008] Refund item {} not in order {}", itemReq.getOrderItemId(), id);
                    throw new BadRequestException(ErrorCode.OR008);
                }
                BigDecimal maxQty = oi.getQuantity().subtract(alreadyRefundedQty.getOrDefault(oi.getId(), BigDecimal.ZERO));
                if (itemReq.getQuantity().compareTo(maxQty) > 0) {
                    log.warn("[OR008] Refund qty {} exceeds available {} for item {}", itemReq.getQuantity(), maxQty, oi.getId());
                    throw new BadRequestException(ErrorCode.OR008);
                }
                BigDecimal itemAmount = oi.getUnitPrice().multiply(itemReq.getQuantity()).setScale(2, RoundingMode.HALF_UP);
                partialSubtotal = partialSubtotal.add(itemAmount);
                refundItems.add(RefundItem.builder()
                        .orderItem(oi)
                        .quantity(itemReq.getQuantity())
                        .amount(itemAmount)
                        .build());
            }
            // Proportional tax for partial amount
            BigDecimal subtotalRatio = order.getSubtotal().compareTo(BigDecimal.ZERO) > 0
                    ? partialSubtotal.divide(order.getSubtotal(), 10, RoundingMode.HALF_UP) : BigDecimal.ONE;
            BigDecimal partialTax = order.getTax().multiply(subtotalRatio).setScale(2, RoundingMode.HALF_UP);
            refundAmount = partialSubtotal.add(partialTax).setScale(2, RoundingMode.HALF_UP);

            // Restore inventory only for refunded items
            for (RefundItem ri : refundItems) {
                inventoryRepository.findByProductId(ri.getOrderItem().getProduct().getId()).ifPresent(inv -> {
                    inv.setQuantity(inv.getQuantity().add(ri.getQuantity()));
                    inventoryRepository.save(inv);
                });
            }

            // Deduct proportional reward points
            int pointsDeducted = 0;
            if (order.getCustomer() != null && order.getSubtotal().compareTo(BigDecimal.ZERO) > 0) {
                int pointsPerDollar = rewardConfig.getPointsPerDollar();
                if (pointsPerDollar > 0) {
                    int earnedOnPartial = partialSubtotal.multiply(BigDecimal.valueOf(pointsPerDollar)).intValue();
                    if (earnedOnPartial > 0) {
                        Customer customer = order.getCustomer();
                        int current = customer.getRewardPoints() != null ? customer.getRewardPoints() : 0;
                        int newBalance = Math.max(0, current - earnedOnPartial);
                        pointsDeducted = current - newBalance;
                        customer.setRewardPoints(newBalance);
                        customerRepository.save(customer);
                    }
                }
            }

            Refund refund = refundRepository.save(Refund.builder()
                    .order(order)
                    .refundedBy(username)
                    .amount(refundAmount)
                    .refundMethod(order.getPaymentMethod())
                    .reason(request.getReason())
                    .rewardPointsDeducted(pointsDeducted)
                    .build());
            for (RefundItem ri : refundItems) {
                ri.setRefund(refund);
                refundItemRepository.save(ri);
            }
            refund.setItems(refundItems);

            BigDecimal newTotalRefunded = existingRefunded.add(refundAmount);
            order.setStatus(newTotalRefunded.compareTo(order.getTotal()) >= 0 ? OrderStatus.REFUNDED : OrderStatus.PARTIALLY_REFUNDED);
            if (order.getStatus() == OrderStatus.REFUNDED) {
                paymentRepository.findByOrderId(id).ifPresent(p -> {
                    p.setStatus(PaymentStatus.FAILED);
                    paymentRepository.save(p);
                });
            }
            orderRepository.save(order);
            log.info("Order id: {} partial refund by '{}', amount: {}", id, username, refundAmount);
            return buildOrderResponse(order);
        }

        // Full refund
        restoreInventory(order);
        int pointsDeducted = 0;
        if (order.getCustomer() != null) {
            int pointsPerDollar = rewardConfig.getPointsPerDollar();
            if (pointsPerDollar > 0) {
                int earned = order.getSubtotal().multiply(BigDecimal.valueOf(pointsPerDollar)).intValue();
                if (earned > 0) {
                    Customer customer = order.getCustomer();
                    int current = customer.getRewardPoints() != null ? customer.getRewardPoints() : 0;
                    int newBalance = Math.max(0, current - earned);
                    pointsDeducted = current - newBalance;
                    customer.setRewardPoints(newBalance);
                    customerRepository.save(customer);
                }
            }
        }

        order.setStatus(OrderStatus.REFUNDED);
        paymentRepository.findByOrderId(id).ifPresent(p -> {
            p.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(p);
        });
        orderRepository.save(order);

        Refund refund = refundRepository.save(Refund.builder()
                .order(order)
                .refundedBy(username)
                .amount(order.getTotal())
                .refundMethod(order.getPaymentMethod())
                .reason(request != null ? request.getReason() : null)
                .rewardPointsDeducted(pointsDeducted)
                .build());

        log.info("Order id: {} full refund by '{}', amount: {}", id, username, order.getTotal());
        return buildOrderResponse(order);
    }

    public com.pos.dto.response.OrderStats getStats() {
        log.debug("Fetching order stats");
        return new com.pos.dto.response.OrderStats(
                orderRepository.count(),
                orderRepository.countByStatus(OrderStatus.COMPLETED),
                orderRepository.countByStatus(OrderStatus.PENDING),
                orderRepository.countByStatus(OrderStatus.CANCELLED),
                orderRepository.countByStatus(OrderStatus.REFUNDED),
                orderRepository.sumCompletedRevenue()
        );
    }

    @Transactional(readOnly = true)
    public void sendReceiptEmail(Long orderId) {
        log.info("Sending receipt email for order id: {}", orderId);
        Order order = findById(orderId);
        if (order.getCustomer() == null) {
            throw new BadRequestException(ErrorCode.OR005);
        }
        String email = order.getCustomer().getEmail();
        if (email == null || email.trim().isBlank()) {
            throw new BadRequestException(ErrorCode.OR005);
        }
        Company company = companyRepository.findFirstByOrderByIdAsc()
                .orElseThrow(() -> new BadRequestException(ErrorCode.EM001));
        receiptEmailService.sendReceipt(company, email.trim(), order);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void restoreInventory(Order order) {
        for (OrderItem item : order.getItems()) {
            inventoryRepository.findByProductId(item.getProduct().getId()).ifPresent(inv -> {
                inv.setQuantity(inv.getQuantity().add(item.getQuantity()));
                inventoryRepository.save(inv);
            });
        }
    }

    /**
     * Resolves the effective tax rate for a product:
     *   1. Product has a taxCategory → look up TaxRule → use rule rate
     *   2. Fallback → global company rate (or default 10%)
     */
    private BigDecimal resolveItemTaxRate(Product product,
                                          Map<String, BigDecimal> taxRuleMap,
                                          BigDecimal globalRate) {
        if (product.getTaxCategory() != null && taxRuleMap.containsKey(product.getTaxCategory())) {
            return taxRuleMap.get(product.getTaxCategory());
        }
        return globalRate;
    }

    private Order findById(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.OR001));
    }
}
