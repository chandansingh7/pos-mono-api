package com.pos.service;

import com.pos.dto.request.ProductRequest;
import com.pos.dto.response.ProductResponse;
import com.pos.entity.Category;
import com.pos.entity.Inventory;
import com.pos.entity.Product;
import com.pos.exception.BadRequestException;
import com.pos.exception.ErrorCode;
import com.pos.exception.ResourceNotFoundException;
import com.pos.repository.CategoryRepository;
import com.pos.repository.InventoryRepository;
import com.pos.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository   productRepository;
    private final CategoryRepository  categoryRepository;
    private final InventoryRepository inventoryRepository;
    private final ImageStorageService imageStorageService;

    public Page<ProductResponse> getAll(String search, Long categoryId, Pageable pageable) {
        log.debug("Fetching products — search: '{}', categoryId: {}", search, categoryId);
        if (search != null && !search.isBlank()) {
            return productRepository.searchActive(search, pageable).map(this::toResponse);
        }
        if (categoryId != null) {
            return productRepository.findByCategoryIdAndActiveTrue(categoryId, pageable).map(this::toResponse);
        }
        return productRepository.findByActiveTrue(pageable).map(this::toResponse);
    }

    public ProductResponse getById(Long id) {
        log.debug("Fetching product id: {}", id);
        return toResponse(findById(id));
    }

    public ProductResponse getByBarcode(String barcode) {
        log.debug("Fetching product by barcode: {}", barcode);
        return toResponse(productRepository.findByBarcode(barcode)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.PR001, "barcode: " + barcode)));
    }

    @Transactional
    public ProductResponse create(ProductRequest request) {
        log.info("Creating product — name: '{}', sku: '{}'", request.getName(), request.getSku());
        if (request.getSku() != null && productRepository.existsBySku(request.getSku())) {
            log.warn("[PR002] SKU already exists: {}", request.getSku());
            throw new BadRequestException(ErrorCode.PR002);
        }
        if (request.getBarcode() != null && productRepository.existsByBarcode(request.getBarcode())) {
            log.warn("[PR003] Barcode already exists: {}", request.getBarcode());
            throw new BadRequestException(ErrorCode.PR003);
        }

        Category category = resolveCategory(request.getCategoryId());

        String unitType = request.getSaleUnitType() != null && !request.getSaleUnitType().isBlank() ? request.getSaleUnitType().trim().toUpperCase() : "PIECE";
        String unit = request.getSaleUnit() != null && !request.getSaleUnit().isBlank() ? request.getSaleUnit().trim().toLowerCase() : "each";
        String taxCat = request.getTaxCategory() != null && !request.getTaxCategory().isBlank()
                ? request.getTaxCategory().trim().toUpperCase() : null;
        Product product = Product.builder()
                .name(request.getName())
                .sku(request.getSku())
                .barcode(request.getBarcode())
                .size(request.getSize())
                .color(request.getColor())
                .price(request.getPrice())
                .saleUnitType(unitType)
                .saleUnit(unit)
                .taxCategory(taxCat)
                .category(category)
                .imageUrl(request.getImageUrl())
                .active(request.isActive())
                .updatedBy(currentUsername())
                .build();
        product = productRepository.save(product);

        inventoryRepository.save(Inventory.builder()
                .product(product)
                .quantity(java.math.BigDecimal.valueOf(request.getInitialStock()))
                .lowStockThreshold(request.getLowStockThreshold())
                .build());

        log.info("Product created — id: {}, name: '{}'", product.getId(), product.getName());
        return toResponse(product);
    }

    @Transactional
    public ProductResponse update(Long id, ProductRequest request) {
        log.info("Updating product id: {}", id);
        Product product = findById(id);

        if (request.getSku() != null && !request.getSku().equals(product.getSku())
                && productRepository.existsBySku(request.getSku())) {
            log.warn("[PR002] SKU already exists: {}", request.getSku());
            throw new BadRequestException(ErrorCode.PR002);
        }

        Category category = resolveCategory(request.getCategoryId());
        product.setName(request.getName());
        product.setSku(request.getSku());
        product.setBarcode(request.getBarcode());
        product.setSize(request.getSize());
        product.setColor(request.getColor());
        product.setPrice(request.getPrice());
        if (request.getSaleUnitType() != null && !request.getSaleUnitType().isBlank()) {
            product.setSaleUnitType(request.getSaleUnitType().trim().toUpperCase());
        }
        if (request.getSaleUnit() != null && !request.getSaleUnit().isBlank()) {
            product.setSaleUnit(request.getSaleUnit().trim().toLowerCase());
        }
        product.setCategory(category);
        if (request.getImageUrl() != null && !request.getImageUrl().isBlank()) {
            product.setImageUrl(request.getImageUrl());
        }
        // taxCategory: null clears override (product reverts to global rate)
        product.setTaxCategory(request.getTaxCategory() != null && !request.getTaxCategory().isBlank()
                ? request.getTaxCategory().trim().toUpperCase() : null);
        product.setActive(request.isActive());
        product.setUpdatedBy(currentUsername());

        log.info("Product updated — id: {}", id);
        return toResponse(productRepository.save(product));
    }

    public void delete(Long id) {
        log.info("Soft-deleting product id: {}", id);
        Product product = findById(id);
        product.setActive(false);
        productRepository.save(product);
    }

    @Transactional
    public ProductResponse uploadImage(Long id, MultipartFile file) {
        log.info("Uploading image for product id: {}", id);
        if (file == null || file.isEmpty()) {
            throw new BadRequestException(ErrorCode.PR005);
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new BadRequestException(ErrorCode.PR006);
        }
        Product product = findById(id);
        try {
            String imageUrl = imageStorageService.store(id, file);
            product.setImageUrl(imageUrl);
            product.setUpdatedBy(currentUsername());
            log.info("Image uploaded for product id: {} — url: {}", id, imageUrl);
            return toResponse(productRepository.save(product));
        } catch (IOException ex) {
            log.error("[SV002] Failed to store image for product id: {}", id, ex);
            throw new RuntimeException(ErrorCode.SV002.getMessage(), ex);
        }
    }

    private Category resolveCategory(Long categoryId) {
        if (categoryId == null) return null;
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CT001));
    }

    public com.pos.dto.response.ProductStats getStats() {
        log.debug("Fetching product stats");
        return new com.pos.dto.response.ProductStats(
                productRepository.count(),
                productRepository.countByActiveTrue(),
                productRepository.countByActiveFalse(),
                inventoryRepository.countOutOfStock()
        );
    }

    private Product findById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.PR001));
    }

    private String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "system";
    }

    private ProductResponse toResponse(Product product) {
        java.math.BigDecimal qty = inventoryRepository.findByProductId(product.getId())
                .map(Inventory::getQuantity).orElse(java.math.BigDecimal.ZERO);
        return ProductResponse.from(product, qty);
    }
}
