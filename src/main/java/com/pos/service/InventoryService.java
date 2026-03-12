package com.pos.service;

import com.pos.dto.request.InventoryUpdateRequest;
import com.pos.dto.response.InventoryResponse;
import com.pos.entity.Inventory;
import com.pos.exception.ErrorCode;
import com.pos.exception.ResourceNotFoundException;
import com.pos.repository.InventoryRepository;
import com.pos.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final ProductRepository   productRepository;

    public Page<InventoryResponse> getAll(String search, Pageable pageable) {
        log.debug("Fetching inventory — search: '{}', page: {}", search, pageable.getPageNumber());
        if (search != null && !search.isBlank()) {
            return inventoryRepository.findAllWithProductSearch(search.trim(), pageable).map(InventoryResponse::from);
        }
        return inventoryRepository.findAllWithProduct(pageable).map(InventoryResponse::from);
    }

    public List<InventoryResponse> getLowStock() {
        log.debug("Fetching low-stock items");
        List<InventoryResponse> items = inventoryRepository.findLowStockItems().stream()
                .map(InventoryResponse::from)
                .collect(Collectors.toList());
        if (!items.isEmpty()) {
            log.info("Low-stock alert: {} product(s) below threshold", items.size());
        }
        return items;
    }

    public InventoryResponse getByProductId(Long productId) {
        log.debug("Fetching inventory for product id: {}", productId);
        return InventoryResponse.from(inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.IN001)));
    }

    public InventoryResponse updateStock(Long productId, InventoryUpdateRequest request) {
        log.info("Updating stock for product id: {} — qty: {}", productId, request.getQuantity());
        if (!productRepository.existsById(productId)) {
            throw new ResourceNotFoundException(ErrorCode.PR001);
        }
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.IN001));

        java.math.BigDecimal oldQty = inventory.getQuantity();
        inventory.setQuantity(request.getQuantity());
        inventory.setLowStockThreshold(request.getLowStockThreshold());
        inventory.setUpdatedBy(currentUsername());
        InventoryResponse saved = InventoryResponse.from(inventoryRepository.save(inventory));
        log.info("Stock updated for product id: {} — {} → {}", productId, oldQty, request.getQuantity());
        return saved;
    }

    public com.pos.dto.response.InventoryStats getStats() {
        log.debug("Fetching inventory stats");
        return new com.pos.dto.response.InventoryStats(
                inventoryRepository.count(),
                inventoryRepository.countInStock(),
                inventoryRepository.countLowStock(),
                inventoryRepository.countOutOfStock()
        );
    }

    private String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "system";
    }
}
