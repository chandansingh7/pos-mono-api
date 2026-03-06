package com.pos.service;

import com.pos.dto.response.BulkUploadResult;
import com.pos.entity.Category;
import com.pos.entity.Inventory;
import com.pos.entity.Product;
import com.pos.repository.CategoryRepository;
import com.pos.repository.InventoryRepository;
import com.pos.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductBulkServiceTest {

    @Mock private ProductRepository productRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private InventoryRepository inventoryRepository;

    @InjectMocks
    private ProductBulkService productBulkService;

    private Product existingProduct;
    private Inventory existingInventory;
    private Category category;

    @BeforeEach
    void setUp() {
        category = Category.builder().id(1L).name("Electronics").build();
        existingProduct = Product.builder()
                .id(10L)
                .name("Laptop")
                .sku("SKU-100")
                .price(new BigDecimal("999.99"))
                .category(category)
                .active(true)
                .build();
        existingInventory = Inventory.builder()
                .id(1L)
                .product(existingProduct)
                .quantity(BigDecimal.valueOf(50))
                .lowStockThreshold(10)
                .build();
    }

    @Test
    void findExistingSkus_emptyList_returnsEmpty() {
        List<String> result = productBulkService.findExistingSkus(List.of());
        assertThat(result).isEmpty();
    }

    @Test
    void findExistingSkus_null_returnsEmpty() {
        List<String> result = productBulkService.findExistingSkus(null);
        assertThat(result).isEmpty();
    }

    @Test
    void findExistingSkus_withBlanks_filtersAndQueries() {
        when(productRepository.findSkusBySkuIn(List.of("A", "B"))).thenReturn(List.of("A"));
        List<String> result = productBulkService.findExistingSkus(List.of("", "A", "  ", "B", "A"));
        assertThat(result).containsExactly("A");
    }

    @Test
    void findExistingSkus_returnsOnlyExistingFromRepo() {
        when(productRepository.findSkusBySkuIn(List.of("SKU-100", "SKU-999"))).thenReturn(List.of("SKU-100"));
        List<String> result = productBulkService.findExistingSkus(List.of("SKU-100", "SKU-999"));
        assertThat(result).containsExactly("SKU-100");
    }

    @Test
    void generateCsvTemplate_returnsHeaderAndExampleRow() {
        byte[] bytes = productBulkService.generateCsvTemplate();
        String content = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        assertThat(content).contains("Name,SKU,Barcode,Price,Category,Initial Stock,Low Stock Threshold");
        assertThat(content).contains("Example Product");
        assertThat(content).contains("SKU-001");
        assertThat(content).contains("9.99");
    }

    @Test
    void generateExcelTemplate_returnsNonEmptyBytes() {
        byte[] bytes = productBulkService.generateExcelTemplate();
        assertThat(bytes).isNotEmpty();
    }

    @Test
    void processUpload_csvNewProduct_savesProductAndInventory() {
        String csv = "Name,SKU,Barcode,Price,Category,Initial Stock,Low Stock Threshold\n"
                + "New Item,NEW-001,,19.99,Electronics,25,5";
        MockMultipartFile file = new MockMultipartFile("file", "test.csv", "text/csv", csv.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        when(categoryRepository.findByName("Electronics")).thenReturn(Optional.of(category));
        when(productRepository.findBySku("NEW-001")).thenReturn(Optional.empty());
        Product savedProduct = Product.builder().id(99L).name("New Item").sku("NEW-001").price(new BigDecimal("19.99")).build();
        when(productRepository.saveAll(anyList())).thenReturn(List.of(savedProduct));
        when(inventoryRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        BulkUploadResult result = productBulkService.processUpload(file, "admin");

        assertThat(result.getTotalRows()).isEqualTo(1);
        assertThat(result.getSuccessCount()).isEqualTo(1);
        assertThat(result.getUpdatedCount()).isEqualTo(0);
        assertThat(result.getFailCount()).isEqualTo(0);
        assertThat(result.getErrors()).isEmpty();
        verify(productRepository).saveAll(anyList());
        verify(inventoryRepository).saveAll(anyList());
    }

    @Test
    void processUpload_csvUpdateBySku_addsQuantityAndSaves() {
        String csv = "Name,SKU,Barcode,Price,Category,Initial Stock,Low Stock Threshold\n"
                + "Laptop Updated,SKU-100,,999.99,Electronics,10,10";
        MockMultipartFile file = new MockMultipartFile("file", "test.csv", "text/csv", csv.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        when(categoryRepository.findByName("Electronics")).thenReturn(Optional.of(category));
        when(productRepository.findBySku("SKU-100")).thenReturn(Optional.of(existingProduct));
        when(inventoryRepository.findByProductId(10L)).thenReturn(Optional.of(existingInventory));
        when(productRepository.saveAll(anyList())).thenReturn(List.of(existingProduct));
        when(inventoryRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        BulkUploadResult result = productBulkService.processUpload(file, "admin");

        assertThat(result.getSuccessCount()).isEqualTo(0);
        assertThat(result.getUpdatedCount()).isEqualTo(1);
        assertThat(existingInventory.getQuantity()).isEqualByComparingTo(BigDecimal.valueOf(60)); // 50 + 10
        verify(productRepository).saveAll(anyList());
        verify(inventoryRepository).saveAll(anyList());
    }

    @Test
    void processUpload_csvInvalidPrice_returnsError() {
        String csv = "Name,SKU,Barcode,Price,Category,Initial Stock,Low Stock Threshold\n"
                + "Bad Price,BAD-001,,,Electronics,0,10";
        MockMultipartFile file = new MockMultipartFile("file", "test.csv", "text/csv", csv.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        when(productRepository.findBySku("BAD-001")).thenReturn(Optional.empty());

        BulkUploadResult result = productBulkService.processUpload(file, "admin");

        assertThat(result.getSuccessCount()).isEqualTo(0);
        assertThat(result.getUpdatedCount()).isEqualTo(0);
        assertThat(result.getErrors()).isNotEmpty();
        assertThat(result.getErrors().get(0).getField()).isEqualTo("Price");
    }

    @Test
    void processUpload_csvDuplicateBarcode_returnsError() {
        String csv = "Name,SKU,Barcode,Price,Category,Initial Stock,Low Stock Threshold\n"
                + "Dup,DUPE-001,1234567890,9.99,Electronics,0,10";
        MockMultipartFile file = new MockMultipartFile("file", "test.csv", "text/csv", csv.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        when(productRepository.findBySku("DUPE-001")).thenReturn(Optional.empty());
        when(categoryRepository.findByName("Electronics")).thenReturn(Optional.of(category));
        when(productRepository.existsByBarcode("1234567890")).thenReturn(true);

        BulkUploadResult result = productBulkService.processUpload(file, "admin");

        assertThat(result.getSuccessCount()).isEqualTo(0);
        assertThat(result.getErrors()).anyMatch(e -> e.getMessage().contains("Barcode already exists"));
    }

    @Test
    void processUpload_csvEmptyFile_returnsZeroRows() {
        String csv = "Name,SKU,Barcode,Price,Category,Initial Stock,Low Stock Threshold\n";
        MockMultipartFile file = new MockMultipartFile("file", "empty.csv", "text/csv", csv.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        BulkUploadResult result = productBulkService.processUpload(file, "admin");

        assertThat(result.getTotalRows()).isEqualTo(0);
        assertThat(result.getSuccessCount()).isEqualTo(0);
        assertThat(result.getUpdatedCount()).isEqualTo(0);
    }

    @Test
    void processUpload_csvSkunOnlyRow_updatesExistingAndAddsStock() {
        String csv = "Name,SKU,Barcode,Price,Category,Initial Stock,Low Stock Threshold\n"
                + ",SKU-100,,, ,20,5";
        MockMultipartFile file = new MockMultipartFile("file", "sku-only.csv", "text/csv", csv.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        when(productRepository.findBySku("SKU-100")).thenReturn(Optional.of(existingProduct));
        when(inventoryRepository.findByProductId(10L)).thenReturn(Optional.of(existingInventory));
        when(productRepository.saveAll(anyList())).thenReturn(List.of(existingProduct));
        when(inventoryRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        BulkUploadResult result = productBulkService.processUpload(file, "admin");

        assertThat(result.getUpdatedCount()).isEqualTo(1);
        assertThat(existingInventory.getQuantity()).isEqualByComparingTo(BigDecimal.valueOf(70)); // 50 + 20
    }
}
