package com.pos.service;

import com.pos.dto.request.LabelRequest;
import com.pos.dto.response.LabelResponse;
import com.pos.entity.Category;
import com.pos.entity.Inventory;
import com.pos.entity.Label;
import com.pos.entity.Product;
import com.pos.exception.BadRequestException;
import com.pos.exception.ResourceNotFoundException;
import com.pos.repository.CategoryRepository;
import com.pos.repository.InventoryRepository;
import com.pos.repository.LabelRepository;
import com.pos.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LabelServiceTest {

    @Mock private LabelRepository labelRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private ProductRepository productRepository;
    @Mock private InventoryRepository inventoryRepository;

    @InjectMocks
    private LabelService labelService;

    private Label sampleLabel;
    private Category sampleCategory;

    @BeforeEach
    void setUp() {
        sampleCategory = Category.builder().id(1L).name("Electronics").build();
        sampleLabel = Label.builder()
                .id(1L)
                .barcode("4901234560011")
                .name("Wireless Earbuds")
                .price(new BigDecimal("49.99"))
                .sku("SKU-E001")
                .category(sampleCategory)
                .product(null)
                .build();
    }

    @Test
    void create_validRequest_returnsLabelResponse() {
        LabelRequest request = new LabelRequest();
        request.setBarcode("4901234560011");
        request.setName("Wireless Earbuds");
        request.setPrice(new BigDecimal("49.99"));
        request.setSku("SKU-E001");
        request.setCategoryId(1L);

        when(labelRepository.existsByBarcode("4901234560011")).thenReturn(false);
        when(productRepository.existsByBarcode("4901234560011")).thenReturn(false);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(sampleCategory));
        when(labelRepository.save(any(Label.class))).thenAnswer(inv -> {
            Label l = inv.getArgument(0);
            l.setId(1L);
            return l;
        });

        LabelResponse response = labelService.create(request);

        assertThat(response.getBarcode()).isEqualTo("4901234560011");
        assertThat(response.getName()).isEqualTo("Wireless Earbuds");
        assertThat(response.getPrice()).isEqualByComparingTo("49.99");
    }

    @Test
    void create_duplicateBarcode_throwsBadRequest() {
        LabelRequest request = new LabelRequest();
        request.setBarcode("4901234560011");
        request.setName("Test");
        request.setPrice(new BigDecimal("9.99"));

        when(labelRepository.existsByBarcode("4901234560011")).thenReturn(true);

        assertThatThrownBy(() -> labelService.create(request))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void create_blankBarcode_autoGenerates() {
        LabelRequest request = new LabelRequest();
        request.setBarcode("");
        request.setName("Auto Label");
        request.setPrice(new BigDecimal("9.99"));

        when(labelRepository.existsByBarcode(anyString())).thenReturn(false);
        when(productRepository.existsByBarcode(anyString())).thenReturn(false);
        when(labelRepository.save(any(Label.class))).thenAnswer(inv -> {
            Label l = inv.getArgument(0);
            l.setId(2L);
            return l;
        });

        LabelResponse response = labelService.create(request);

        assertThat(response.getName()).isEqualTo("Auto Label");
        assertThat(response.getBarcode()).isNotBlank();
        assertThat(response.getBarcode()).startsWith("LBL");
    }

    @Test
    void getById_existingLabel_returnsResponse() {
        when(labelRepository.findById(1L)).thenReturn(Optional.of(sampleLabel));

        LabelResponse response = labelService.getById(1L);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getName()).isEqualTo("Wireless Earbuds");
    }

    @Test
    void getById_nonExisting_throwsNotFound() {
        when(labelRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> labelService.getById(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void addAsProduct_validLabel_createsProduct() {
        when(labelRepository.findById(1L)).thenReturn(Optional.of(sampleLabel));
        when(productRepository.existsByBarcode("4901234560011")).thenReturn(false);
        when(productRepository.existsBySku("SKU-E001")).thenReturn(false);

        Product savedProduct = Product.builder()
                .id(10L)
                .name("Wireless Earbuds")
                .barcode("4901234560011")
                .sku("SKU-E001")
                .price(new BigDecimal("49.99"))
                .category(sampleCategory)
                .build();
        when(productRepository.save(any(Product.class))).thenReturn(savedProduct);

        Inventory savedInv = Inventory.builder().product(savedProduct).quantity(new BigDecimal("5")).build();
        when(inventoryRepository.save(any(Inventory.class))).thenReturn(savedInv);
        when(inventoryRepository.findByProductId(10L)).thenReturn(Optional.of(savedInv));

        var response = labelService.addAsProduct(1L, 5);

        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getName()).isEqualTo("Wireless Earbuds");
    }

    @Test
    void attachToProduct_setsBarcodeWhenMissing() {
        Product product = Product.builder()
                .id(20L)
                .name("Existing Product")
                .price(new BigDecimal("9.99"))
                .active(true)
                .build(); // no barcode yet

        when(labelRepository.findById(1L)).thenReturn(Optional.of(sampleLabel));
        when(productRepository.findById(20L)).thenReturn(Optional.of(product));
        when(labelRepository.save(any(Label.class))).thenAnswer(inv -> inv.getArgument(0));

        LabelResponse response = labelService.attachToProduct(1L, 20L);

        assertThat(response.getProductId()).isEqualTo(20L);
        assertThat(product.getBarcode()).isEqualTo(sampleLabel.getBarcode());
        verify(productRepository).save(product);
    }

    @Test
    void attachToProduct_differentExistingBarcode_throwsBadRequest() {
        Product product = Product.builder()
                .id(21L)
                .name("Existing Product")
                .barcode("OTHER-CODE")
                .price(new BigDecimal("9.99"))
                .active(true)
                .build();

        when(labelRepository.findById(1L)).thenReturn(Optional.of(sampleLabel));
        when(productRepository.findById(21L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> labelService.attachToProduct(1L, 21L))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void attachToProduct_forceOverride_overwritesBarcode() {
        Product product = Product.builder()
                .id(22L)
                .name("Existing Product")
                .barcode("OTHER-CODE")
                .price(new BigDecimal("9.99"))
                .active(true)
                .build();

        when(labelRepository.findById(1L)).thenReturn(Optional.of(sampleLabel));
        when(productRepository.findById(22L)).thenReturn(Optional.of(product));
        when(labelRepository.save(any(Label.class))).thenAnswer(inv -> inv.getArgument(0));

        LabelResponse response = labelService.attachToProduct(1L, 22L, true);

        assertThat(response.getProductId()).isEqualTo(22L);
        assertThat(product.getBarcode()).isEqualTo(sampleLabel.getBarcode());
        verify(productRepository).save(product);
    }
}
