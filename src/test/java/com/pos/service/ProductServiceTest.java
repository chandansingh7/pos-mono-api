package com.pos.service;

import com.pos.dto.request.ProductRequest;
import com.pos.dto.response.ProductResponse;
import com.pos.entity.Category;
import com.pos.entity.Inventory;
import com.pos.entity.Product;
import com.pos.exception.BadRequestException;
import com.pos.exception.ResourceNotFoundException;
import com.pos.repository.CategoryRepository;
import com.pos.repository.InventoryRepository;
import com.pos.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock private ProductRepository productRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private InventoryRepository inventoryRepository;

    @InjectMocks
    private ProductService productService;

    private Product sampleProduct;
    private Inventory sampleInventory;

    @BeforeEach
    void setUp() {
        sampleProduct = Product.builder()
                .id(1L)
                .name("Laptop")
                .sku("LAP-001")
                .price(new BigDecimal("999.99"))
                .active(true)
                .build();

        sampleInventory = Inventory.builder()
                .id(1L)
                .product(sampleProduct)
                .quantity(BigDecimal.valueOf(50))
                .lowStockThreshold(10)
                .build();
    }

    @Test
    void getById_existingProduct_returnsResponse() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct));
        when(inventoryRepository.findByProductId(1L)).thenReturn(Optional.of(sampleInventory));

        ProductResponse response = productService.getById(1L);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getName()).isEqualTo("Laptop");
        assertThat(response.getPrice()).isEqualByComparingTo("999.99");
        assertThat(response.getQuantity()).isEqualByComparingTo(BigDecimal.valueOf(50));
    }

    @Test
    void getById_nonExistingProduct_throwsNotFound() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getById(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void create_withDuplicateSku_throwsBadRequest() {
        ProductRequest request = new ProductRequest();
        request.setName("Mouse");
        request.setSku("LAP-001");
        request.setPrice(new BigDecimal("29.99"));

        when(productRepository.existsBySku("LAP-001")).thenReturn(true);

        assertThatThrownBy(() -> productService.create(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("SKU already exists");
    }

    @Test
    void create_validRequest_savesProductAndInventory() {
        ProductRequest request = new ProductRequest();
        request.setName("Mouse");
        request.setSku("MOU-001");
        request.setPrice(new BigDecimal("29.99"));
        request.setInitialStock(100);

        when(productRepository.existsBySku("MOU-001")).thenReturn(false);
        when(productRepository.save(any())).thenReturn(sampleProduct);
        when(inventoryRepository.save(any())).thenReturn(sampleInventory);
        when(inventoryRepository.findByProductId(any())).thenReturn(Optional.of(sampleInventory));

        ProductResponse response = productService.create(request);

        assertThat(response).isNotNull();
        verify(productRepository).save(any(Product.class));
        verify(inventoryRepository).save(any(Inventory.class));
    }

    @Test
    void delete_existingProduct_deactivatesProduct() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct));
        when(productRepository.save(any())).thenReturn(sampleProduct);

        productService.delete(1L);

        verify(productRepository).save(argThat(p -> !p.isActive()));
    }

    @Test
    void getStats_returnsAggregatedCounts() {
        when(productRepository.count()).thenReturn(10L);
        when(productRepository.countByActiveTrue()).thenReturn(7L);
        when(productRepository.countByActiveFalse()).thenReturn(3L);
        when(inventoryRepository.countOutOfStock()).thenReturn(2L);

        var stats = productService.getStats();

        assertThat(stats.total()).isEqualTo(10L);
        assertThat(stats.active()).isEqualTo(7L);
        assertThat(stats.inactive()).isEqualTo(3L);
        assertThat(stats.outOfStock()).isEqualTo(2L);
    }
}
