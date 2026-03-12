package com.pos.controller;

import com.pos.dto.request.InventoryUpdateRequest;
import com.pos.dto.response.ApiResponse;
import com.pos.dto.response.InventoryResponse;
import com.pos.service.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<Page<InventoryResponse>>> getAll(
            @RequestParam(required = false) String search,
            Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(inventoryService.getAll(search, pageable)));
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<com.pos.dto.response.InventoryStats>> getStats() {
        return ResponseEntity.ok(ApiResponse.ok(inventoryService.getStats()));
    }

    @GetMapping("/low-stock")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<List<InventoryResponse>>> getLowStock() {
        return ResponseEntity.ok(ApiResponse.ok(inventoryService.getLowStock()));
    }

    @GetMapping("/product/{productId}")
    public ResponseEntity<ApiResponse<InventoryResponse>> getByProduct(@PathVariable Long productId) {
        return ResponseEntity.ok(ApiResponse.ok(inventoryService.getByProductId(productId)));
    }

    @PutMapping("/product/{productId}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<InventoryResponse>> updateStock(@PathVariable Long productId,
                                                                      @Valid @RequestBody InventoryUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Stock updated", inventoryService.updateStock(productId, request)));
    }
}
