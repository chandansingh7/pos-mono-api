package com.pos.controller;

import com.pos.dto.response.BulkUploadResult;
import com.pos.dto.response.ProductResponse;
import com.pos.service.ProductBulkService;
import com.pos.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProductService productService;
    @MockBean
    private ProductBulkService productBulkService;

    @Test
    @WithMockUser(authorities = "ADMIN")
    void bulkUpload_csv_returnsResult() throws Exception {
        String csv = "Name,SKU,Barcode,Price,Category,Initial Stock,Low Stock Threshold\n"
                + "Product A,A-001,,9.99,Electronics,10,5";
        MockMultipartFile file = new MockMultipartFile("file", "test.csv", "text/csv", csv.getBytes());

        BulkUploadResult result = BulkUploadResult.builder()
                .totalRows(1)
                .successCount(1)
                .updatedCount(0)
                .failCount(0)
                .errors(List.of())
                .build();
        when(productBulkService.processUpload(any(), eq("user"))).thenReturn(result);

        mockMvc.perform(multipart("/api/products/bulk-upload").file(file).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.successCount").value(1))
                .andExpect(jsonPath("$.data.updatedCount").value(0))
                .andExpect(jsonPath("$.data.failCount").value(0));
    }

    @Test
    @WithMockUser(authorities = "MANAGER")
    void bulkCheckSkus_returnsExistingSkus() throws Exception {
        when(productBulkService.findExistingSkus(List.of("A-001", "B-002"))).thenReturn(List.of("A-001"));

        mockMvc.perform(post("/api/products/bulk-check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[\"A-001\", \"B-002\"]")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0]").value("A-001"));
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void get_all_returnsPage() throws Exception {
        ProductResponse p = ProductResponse.builder()
                .id(1L)
                .name("Test")
                .price(BigDecimal.TEN)
                .active(true)
                .quantity(BigDecimal.ZERO)
                .build();
        when(productService.getAll(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(p)));

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].name").value("Test"));
    }
}
