package com.pos.service;

import com.pos.config.RewardConfig;
import com.pos.dto.request.OrderItemRequest;
import com.pos.dto.request.OrderRequest;
import com.pos.dto.response.OrderResponse;
import com.pos.entity.*;
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
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private static final BigDecimal TAX_RATE = new BigDecimal("0.10");

    private final OrderRepository     orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository   productRepository;
    private final InventoryRepository inventoryRepository;
    private final CustomerRepository  customerRepository;
    private final UserRepository      userRepository;
    private final PaymentRepository   paymentRepository;
    private final RewardConfig        rewardConfig;

    public Page<OrderResponse> getAll(Pageable pageable) {
        log.debug("Fetching orders — page: {}", pageable.getPageNumber());
        return orderRepository.findAll(pageable).map(OrderResponse::from);
    }

    public OrderResponse getById(Long id) {
        log.debug("Fetching order id: {}", id);
        return OrderResponse.from(findById(id));
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
            BigDecimal redemptionDollars = BigDecimal.valueOf(pointsToRedeem).divide(BigDecimal.valueOf(rate), 2, RoundingMode.DOWN);
            discount = discount.add(redemptionDollars);
            customer.setRewardPoints(balance - pointsToRedeem);
            customerRepository.save(customer);
        }

        List<OrderItem> items           = new ArrayList<>();
        List<Inventory> inventoriesToSave = new ArrayList<>();
        BigDecimal      subtotal       = BigDecimal.ZERO;

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
        }

        inventoryRepository.saveAll(inventoriesToSave);

        BigDecimal afterDiscount = subtotal.subtract(discount).max(BigDecimal.ZERO);
        BigDecimal tax          = afterDiscount.multiply(TAX_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal total        = afterDiscount.add(tax).setScale(2, RoundingMode.HALF_UP);

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
                    log.info("Order id: {} — customer {} earned {} reward points", order.getId(), customer.getId(), earned);
                }
            }
        }

        log.info("Order created — id: {}, total: {}", order.getId(), total);
        return OrderResponse.from(order);
    }

    @Transactional
    public OrderResponse cancel(Long id) {
        log.info("Cancelling order id: {}", id);
        Order order = findById(id);

        if (order.getStatus() == OrderStatus.CANCELLED) {
            log.warn("[OR003] Cancel rejected — order id: {} already cancelled", id);
            throw new BadRequestException(ErrorCode.OR003);
        }
        if (order.getStatus() == OrderStatus.REFUNDED) {
            log.warn("[OR004] Cancel rejected — order id: {} is refunded", id);
            throw new BadRequestException(ErrorCode.OR004);
        }

        for (OrderItem item : order.getItems()) {
            inventoryRepository.findByProductId(item.getProduct().getId()).ifPresent(inv -> {
                inv.setQuantity(inv.getQuantity().add(item.getQuantity()));
                inventoryRepository.save(inv);
            });
        }

        order.setStatus(OrderStatus.CANCELLED);
        paymentRepository.findByOrderId(id).ifPresent(p -> {
            p.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(p);
        });

        log.info("Order id: {} cancelled", id);
        return OrderResponse.from(orderRepository.save(order));
    }

    public com.pos.dto.response.OrderStats getStats() {
        log.debug("Fetching order stats");
        return new com.pos.dto.response.OrderStats(
                orderRepository.count(),
                orderRepository.countByStatus(com.pos.enums.OrderStatus.COMPLETED),
                orderRepository.countByStatus(com.pos.enums.OrderStatus.PENDING),
                orderRepository.countByStatus(com.pos.enums.OrderStatus.CANCELLED),
                orderRepository.countByStatus(com.pos.enums.OrderStatus.REFUNDED),
                orderRepository.sumCompletedRevenue()
        );
    }

    private Order findById(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.OR001));
    }
}
