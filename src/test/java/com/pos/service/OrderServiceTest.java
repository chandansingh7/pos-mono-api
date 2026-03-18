package com.pos.service;

import com.pos.config.RewardConfig;
import com.pos.dto.request.OrderItemRequest;
import com.pos.dto.request.OrderRequest;
import com.pos.dto.request.RefundRequest;
import com.pos.dto.response.OrderResponse;
import com.pos.entity.*;
import com.pos.enums.OrderStatus;
import com.pos.enums.PaymentMethod;
import com.pos.enums.PaymentStatus;
import com.pos.exception.BadRequestException;
import com.pos.exception.ResourceNotFoundException;
import com.pos.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private ProductRepository productRepository;
    @Mock private InventoryRepository inventoryRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private UserRepository userRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private CompanyRepository companyRepository;
    @Mock private RefundRepository refundRepository;
    @Mock private TaxRuleRepository taxRuleRepository;
    @Mock private RewardConfig rewardConfig;
    @Mock private ReceiptEmailService receiptEmailService;

    @InjectMocks
    private OrderService orderService;

    private User cashier;
    private Product product;
    private Inventory inventory;
    private Order order;

    @BeforeEach
    void setUp() {
        lenient().when(rewardConfig.getPointsPerDollar()).thenReturn(1);
        lenient().when(rewardConfig.getRedemptionRate()).thenReturn(100);
        lenient().when(taxRuleRepository.findAllByOrderByTaxCategoryAsc()).thenReturn(List.of());
        lenient().when(refundRepository.findByOrderId(any())).thenReturn(Optional.empty());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("cashier1", null, List.of()));
        cashier = new User();
        cashier.setId(1L);
        cashier.setUsername("cashier1");
        product = Product.builder().id(10L).name("Widget").price(new BigDecimal("10.00")).active(true).build();
        inventory = Inventory.builder().id(1L).product(product).quantity(BigDecimal.valueOf(100)).lowStockThreshold(5).build();
        order = Order.builder()
                .id(1L)
                .cashier(cashier)
                .subtotal(new BigDecimal("10.00"))
                .tax(new BigDecimal("1.00"))
                .discount(BigDecimal.ZERO)
                .total(new BigDecimal("11.00"))
                .status(OrderStatus.COMPLETED)
                .paymentMethod(PaymentMethod.CASH)
                .items(List.of())
                .build();
    }

    @Test
    void getById_existing_returnsResponse() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        OrderResponse response = orderService.getById(1L);
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getTotal()).isEqualByComparingTo("11.00");
    }

    @Test
    void getById_notFound_throws() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> orderService.getById(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getAll_returnsPage() {
        when(orderRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(order)));
        var result = orderService.getAll(org.springframework.data.domain.PageRequest.of(0, 10));
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getId()).isEqualTo(1L);
    }

    @Test
    void create_validRequest_savesOrderAndItems() {
        OrderItemRequest itemReq = new OrderItemRequest();
        itemReq.setProductId(10L);
        itemReq.setQuantity(BigDecimal.valueOf(2));
        OrderRequest request = new OrderRequest();
        request.setItems(List.of(itemReq));
        request.setPaymentMethod(PaymentMethod.CASH);
        request.setDiscount(BigDecimal.ZERO);

        when(userRepository.findByUsername("cashier1")).thenReturn(Optional.of(cashier));
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(inventoryRepository.findByProductId(10L)).thenReturn(Optional.of(inventory));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(2L);
            return o;
        });
        when(orderItemRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(inventoryRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = orderService.create(request);

        assertThat(response).isNotNull();
        verify(orderRepository).save(any(Order.class));
        verify(inventoryRepository).saveAll(any());
        assertThat(inventory.getQuantity()).isEqualByComparingTo(BigDecimal.valueOf(98));
    }

    @Test
    void create_insufficientStock_throwsBadRequest() {
        inventory.setQuantity(BigDecimal.ONE);
        OrderItemRequest itemReq = new OrderItemRequest();
        itemReq.setProductId(10L);
        itemReq.setQuantity(BigDecimal.valueOf(5));
        OrderRequest request = new OrderRequest();
        request.setItems(List.of(itemReq));
        request.setPaymentMethod(PaymentMethod.CASH);

        when(userRepository.findByUsername("cashier1")).thenReturn(Optional.of(cashier));
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(inventoryRepository.findByProductId(10L)).thenReturn(Optional.of(inventory));

        assertThatThrownBy(() -> orderService.create(request))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void create_withCustomerAndPointsToRedeem_deductsPointsAndAppliesDiscount() {
        Customer customer = Customer.builder().id(1L).name("Jane").rewardPoints(500).build();
        OrderItemRequest itemReq = new OrderItemRequest();
        itemReq.setProductId(10L);
        itemReq.setQuantity(BigDecimal.ONE);
        OrderRequest request = new OrderRequest();
        request.setCustomerId(1L);
        request.setItems(List.of(itemReq));
        request.setPaymentMethod(PaymentMethod.CASH);
        request.setDiscount(BigDecimal.ZERO);
        request.setPointsToRedeem(200);

        when(userRepository.findByUsername("cashier1")).thenReturn(Optional.of(cashier));
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(inventoryRepository.findByProductId(10L)).thenReturn(Optional.of(inventory));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(2L);
            return o;
        });
        when(orderItemRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(inventoryRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = orderService.create(request);

        assertThat(response).isNotNull();
        // 500 - 200 redeem + 10 earned (subtotal 10 * 1 point per dollar) = 310
        assertThat(customer.getRewardPoints()).isEqualTo(310);
        // Save called twice: once after deducting redemption, once after adding earned points
        verify(customerRepository, times(2)).save(any(Customer.class));
    }

    @Test
    void create_pointsToRedeemExceedsBalance_throwsBadRequest() {
        Customer customer = Customer.builder().id(1L).name("Jane").rewardPoints(50).build();
        OrderItemRequest itemReq = new OrderItemRequest();
        itemReq.setProductId(10L);
        itemReq.setQuantity(BigDecimal.ONE);
        OrderRequest request = new OrderRequest();
        request.setCustomerId(1L);
        request.setItems(List.of(itemReq));
        request.setPaymentMethod(PaymentMethod.CASH);
        request.setPointsToRedeem(100);

        when(userRepository.findByUsername("cashier1")).thenReturn(Optional.of(cashier));
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(inventoryRepository.findByProductId(10L)).thenReturn(Optional.of(inventory));

        assertThatThrownBy(() -> orderService.create(request))
                .isInstanceOf(BadRequestException.class);
        assertThat(customer.getRewardPoints()).isEqualTo(50);
    }

    @Test
    void getStats_returnsCounts() {
        when(orderRepository.count()).thenReturn(100L);
        when(orderRepository.countByStatus(OrderStatus.COMPLETED)).thenReturn(80L);
        when(orderRepository.countByStatus(OrderStatus.PENDING)).thenReturn(5L);
        when(orderRepository.countByStatus(OrderStatus.CANCELLED)).thenReturn(10L);
        when(orderRepository.countByStatus(OrderStatus.REFUNDED)).thenReturn(5L);
        when(orderRepository.sumCompletedRevenue()).thenReturn(BigDecimal.valueOf(5000.00));

        var stats = orderService.getStats();
        assertThat(stats).isNotNull();
        assertThat(stats.total()).isEqualTo(100L);
        assertThat(stats.completed()).isEqualTo(80L);
    }

    // ── Refund tests ─────────────────────────────────────────────────────────

    @Test
    void refund_completedOrder_setsRefundedStatusAndSavesRefundRecord() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(paymentRepository.findByOrderId(1L)).thenReturn(Optional.empty());
        when(refundRepository.save(any())).thenAnswer(inv -> {
            Refund r = inv.getArgument(0);
            r.getClass(); // no-op, just confirm non-null
            return r;
        });

        RefundRequest req = new RefundRequest();
        req.setReason("Customer changed mind");
        OrderResponse response = orderService.refund(1L, req);

        assertThat(response).isNotNull();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUNDED);
        verify(refundRepository, times(1)).save(any(Refund.class));
    }

    @Test
    void refund_alreadyRefundedOrder_throwsBadRequest() {
        order.setStatus(OrderStatus.REFUNDED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.refund(1L, null))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void refund_cancelledOrder_throwsBadRequest() {
        order.setStatus(OrderStatus.CANCELLED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.refund(1L, null))
                .isInstanceOf(BadRequestException.class);
    }

    // ── Tax resolution tests ──────────────────────────────────────────────────

    @Test
    void create_withGlobalTaxRate_appliesCompanyRate() {
        Company company = new Company();
        company.setTaxRate(new BigDecimal("0.08")); // 8%
        company.setTaxEnabled(true);

        lenient().when(companyRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(company));
        when(userRepository.findByUsername("cashier1")).thenReturn(Optional.of(cashier));
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(inventoryRepository.findByProductId(10L)).thenReturn(Optional.of(inventory));
        when(orderRepository.save(any())).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(99L);
            o.setItems(List.of());
            return o;
        });
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OrderItemRequest itemReq = new OrderItemRequest();
        itemReq.setProductId(10L);
        itemReq.setQuantity(BigDecimal.ONE);
        OrderRequest request = new OrderRequest();
        request.setItems(List.of(itemReq));
        request.setPaymentMethod(PaymentMethod.CASH);

        OrderResponse response = orderService.create(request);
        // Tax should be 10.00 * 0.08 = 0.80
        assertThat(response.getTax()).isEqualByComparingTo("0.80");
    }
}
