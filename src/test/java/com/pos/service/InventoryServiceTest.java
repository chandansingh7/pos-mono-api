package com.pos.service;

import com.pos.dto.request.InventoryUpdateRequest;
import com.pos.dto.response.InventoryResponse;
import com.pos.entity.Inventory;
import com.pos.entity.Product;
import com.pos.exception.ResourceNotFoundException;
import com.pos.repository.InventoryRepository;
import com.pos.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock private InventoryRepository inventoryRepository;
    @Mock private ProductRepository productRepository;

    @InjectMocks
    private InventoryService inventoryService;

    private Product product;
    private Inventory inventory;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("testuser", null, List.of()));
        product = Product.builder().id(1L).name("Widget").build();
        inventory = Inventory.builder()
                .id(1L)
                .product(product)
                .quantity(BigDecimal.valueOf(25))
                .lowStockThreshold(5)
                .build();
    }

    @Test
    void getAll_returnsPaginatedList() {
        Pageable pageable = PageRequest.of(0, 10);
        when(inventoryRepository.findAllWithProduct(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(inventory), pageable, 1));
        var result = inventoryService.getAll(pageable);
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getQuantity()).isEqualByComparingTo(BigDecimal.valueOf(25));
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void getByProductId_existing_returnsResponse() {
        when(inventoryRepository.findByProductId(1L)).thenReturn(Optional.of(inventory));
        InventoryResponse response = inventoryService.getByProductId(1L);
        assertThat(response).isNotNull();
        assertThat(response.getQuantity()).isEqualByComparingTo(BigDecimal.valueOf(25));
        assertThat(response.getProductId()).isEqualTo(1L);
    }

    @Test
    void getByProductId_notFound_throws() {
        when(inventoryRepository.findByProductId(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> inventoryService.getByProductId(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateStock_productNotFound_throws() {
        when(productRepository.existsById(99L)).thenReturn(false);
        InventoryUpdateRequest request = new InventoryUpdateRequest();
        request.setQuantity(BigDecimal.valueOf(100));
        assertThatThrownBy(() -> inventoryService.updateStock(99L, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateStock_valid_updatesAndReturns() {
        when(productRepository.existsById(1L)).thenReturn(true);
        when(inventoryRepository.findByProductId(1L)).thenReturn(Optional.of(inventory));
        when(inventoryRepository.save(any(Inventory.class))).thenAnswer(inv -> inv.getArgument(0));
        InventoryUpdateRequest request = new InventoryUpdateRequest();
        request.setQuantity(BigDecimal.valueOf(100));
        request.setLowStockThreshold(10);
        InventoryResponse response = inventoryService.updateStock(1L, request);
        assertThat(response).isNotNull();
        assertThat(inventory.getQuantity()).isEqualByComparingTo(BigDecimal.valueOf(100));
        assertThat(inventory.getLowStockThreshold()).isEqualTo(10);
        verify(inventoryRepository).save(inventory);
    }

    @Test
    void getStats_returnsCounts() {
        when(inventoryRepository.count()).thenReturn(50L);
        when(inventoryRepository.countInStock()).thenReturn(40L);
        when(inventoryRepository.countLowStock()).thenReturn(5L);
        when(inventoryRepository.countOutOfStock()).thenReturn(5L);
        var stats = inventoryService.getStats();
        assertThat(stats).isNotNull();
        assertThat(stats.total()).isEqualTo(50L);
        assertThat(stats.inStock()).isEqualTo(40L);
        assertThat(stats.lowStock()).isEqualTo(5L);
        assertThat(stats.outOfStock()).isEqualTo(5L);
    }
}
