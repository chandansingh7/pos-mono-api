package com.pos.service;

import com.pos.dto.request.LabelRequest;
import com.pos.dto.response.LabelResponse;
import com.pos.dto.response.ProductResponse;
import com.pos.entity.Category;
import com.pos.entity.Inventory;
import com.pos.entity.Label;
import com.pos.entity.Product;
import com.pos.exception.BadRequestException;
import com.pos.exception.ErrorCode;
import com.pos.exception.ResourceNotFoundException;
import com.pos.repository.CategoryRepository;
import com.pos.repository.InventoryRepository;
import com.pos.repository.LabelRepository;
import com.pos.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class LabelService {

    private final LabelRepository labelRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;

    public Page<LabelResponse> getAll(String search, Long categoryId, Pageable pageable) {
        log.debug("Fetching labels — search: '{}', categoryId: {}", search, categoryId);
        if (search != null && !search.isBlank()) {
            if (categoryId != null) {
                return labelRepository.searchUnlinkedByCategory(search, categoryId, pageable)
                        .map(LabelResponse::from);
            }
            return labelRepository.searchUnlinked(search, pageable).map(LabelResponse::from);
        }
        if (categoryId != null) {
            return labelRepository.findByCategoryIdAndUnlinked(categoryId, pageable)
                    .map(LabelResponse::from);
        }
        return labelRepository.findAllUnlinked(pageable).map(LabelResponse::from);
    }

    public LabelResponse getById(Long id) {
        log.debug("Fetching label id: {}", id);
        return LabelResponse.from(findById(id));
    }

    @Transactional
    public LabelResponse create(LabelRequest request) {
        String barcode = resolveBarcode(request.getBarcode());
        log.info("Creating label — barcode: '{}', name: '{}'", barcode, request.getName());

        Category category = resolveCategory(request.getCategoryId());

        Label label = Label.builder()
                .barcode(barcode)
                .name(request.getName().trim())
                .price(request.getPrice())
                .sku(request.getSku() != null ? request.getSku().trim() : null)
                .category(category)
                .build();
        label = labelRepository.save(label);

        log.info("Label created — id: {}, barcode: '{}'", label.getId(), label.getBarcode());
        return LabelResponse.from(label);
    }

    @Transactional
    public List<LabelResponse> createBulk(List<LabelRequest> requests) {
        log.info("Bulk creating {} labels", requests != null ? requests.size() : 0);
        List<LabelResponse> results = new ArrayList<>();
        if (requests == null || requests.isEmpty()) {
            return results;
        }
        for (LabelRequest req : requests) {
            if (req.getName() == null || req.getName().isBlank()) continue;
            results.add(create(req));
        }
        log.info("Bulk created {} labels", results.size());
        return results;
    }

    private String resolveBarcode(String provided) {
        String barcode = provided != null ? provided.trim() : "";
        if (barcode.isBlank()) {
            barcode = generateUniqueBarcode();
        }
        if (labelRepository.existsByBarcode(barcode) || productRepository.existsByBarcode(barcode)) {
            throw new BadRequestException(ErrorCode.LB002);
        }
        return barcode;
    }

    private String generateUniqueBarcode() {
        String candidate;
        int attempts = 0;
        do {
            long base = System.currentTimeMillis() % 10_000_000_000L;
            int suffix = ThreadLocalRandom.current().nextInt(100, 9999);
            candidate = "LBL" + base + suffix;
            attempts++;
            if (attempts > 10) {
                candidate = "LBL" + System.nanoTime() + ThreadLocalRandom.current().nextInt(100, 999);
            }
        } while ((labelRepository.existsByBarcode(candidate) || productRepository.existsByBarcode(candidate))
                && attempts <= 10);
        return candidate;
    }

    @Transactional
    public LabelResponse update(Long id, LabelRequest request) {
        log.info("Updating label id: {}", id);
        Label label = findById(id);
        if (label.getProduct() != null) {
            throw new BadRequestException(ErrorCode.LB001, "Cannot edit a label that is linked to a product");
        }

        String newBarcode = request.getBarcode() != null ? request.getBarcode().trim() : "";
        if (newBarcode.isBlank()) {
            newBarcode = label.getBarcode();
        } else if (!newBarcode.equals(label.getBarcode())) {
            if (labelRepository.existsByBarcode(newBarcode) || productRepository.existsByBarcode(newBarcode)) {
                throw new BadRequestException(ErrorCode.LB002);
            }
        }

        Category category = resolveCategory(request.getCategoryId());
        label.setBarcode(newBarcode);
        label.setName(request.getName().trim());
        label.setPrice(request.getPrice());
        label.setSku(request.getSku() != null ? request.getSku().trim() : null);
        label.setCategory(category);

        log.info("Label updated — id: {}", id);
        return LabelResponse.from(labelRepository.save(label));
    }

    @Transactional
    public ProductResponse addAsProduct(Long labelId, int initialStock) {
        log.info("Converting label {} to product", labelId);
        Label label = findById(labelId);
        if (label.getProduct() != null) {
            throw new BadRequestException(ErrorCode.LB001, "Label is already linked to a product");
        }
        if (productRepository.existsByBarcode(label.getBarcode())) {
            throw new BadRequestException(ErrorCode.LB002);
        }
        if (label.getSku() != null && !label.getSku().isBlank() && productRepository.existsBySku(label.getSku())) {
            throw new BadRequestException(ErrorCode.PR002);
        }

        Product product = Product.builder()
                .name(label.getName())
                .sku(label.getSku())
                .barcode(label.getBarcode())
                .price(label.getPrice())
                .category(label.getCategory())
                .active(true)
                .updatedBy(currentUsername())
                .build();
        product = productRepository.save(product);

        inventoryRepository.save(Inventory.builder()
                .product(product)
                .quantity(java.math.BigDecimal.valueOf(initialStock))
                .lowStockThreshold(10)
                .build());

        label.setProduct(product);
        labelRepository.save(label);

        log.info("Label {} converted to product id: {}", labelId, product.getId());
        return toProductResponse(product);
    }

    /**
     * Attach an existing label to an existing product.
     * <p>
     * HCI safety rules:
     * <ul>
     *   <li>If product has no barcode, it will be set to the label's barcode.</li>
     *   <li>If product has the same barcode, the label is simply linked.</li>
     *   <li>If product has a different barcode and force = false, the operation is rejected with LB002.</li>
     *   <li>If product has a different barcode and force = true, the product barcode is overwritten with the label's barcode.</li>
     * </ul>
     */
    @Transactional
    public LabelResponse attachToProduct(Long labelId, Long productId) {
        return attachToProduct(labelId, productId, false);
    }

    @Transactional
    public LabelResponse attachToProduct(Long labelId, Long productId, boolean force) {
        log.info("Attaching label {} to existing product {} (force={})", labelId, productId, force);
        Label label = findById(labelId);
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.PR001, "id: " + productId));

        if (label.getProduct() != null && !label.getProduct().getId().equals(productId)) {
            throw new BadRequestException(ErrorCode.LB001, "Label is already linked to a different product");
        }

        String labelBarcode = label.getBarcode();
        String productBarcode = product.getBarcode();

        if (productBarcode == null || productBarcode.isBlank()) {
            // Safe: product doesn't have a barcode yet, assign label's barcode.
            product.setBarcode(labelBarcode);
            productRepository.save(product);
        } else if (!productBarcode.equals(labelBarcode)) {
            if (!force) {
                // HCI: avoid silent mismatch between printed barcode and product unless explicitly confirmed.
                log.warn("[LB002] Attach rejected — product {} already has different barcode {}", productId, productBarcode);
                throw new BadRequestException(ErrorCode.LB002,
                        "Product already has a different barcode: " + productBarcode);
            }
            // Explicit override requested: align product barcode with label barcode.
            product.setBarcode(labelBarcode);
            productRepository.save(product);
        }

        label.setProduct(product);
        labelRepository.save(label);

        log.info("Label {} attached to product {}", labelId, productId);
        return LabelResponse.from(label);
    }

    @Transactional
    public void delete(Long id) {
        log.info("Deleting label id: {}", id);
        Label label = findById(id);
        if (label.getProduct() != null) {
            throw new BadRequestException(ErrorCode.LB001, "Cannot delete a label linked to a product");
        }
        labelRepository.delete(label);
    }

    private Label findById(Long id) {
        return labelRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.LB001, "id: " + id));
    }

    private Category resolveCategory(Long categoryId) {
        if (categoryId == null) return null;
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CT001, "id: " + categoryId));
    }

    private String currentUsername() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "system";
    }

    private ProductResponse toProductResponse(Product p) {
        java.math.BigDecimal qty = inventoryRepository.findByProductId(p.getId())
                .map(Inventory::getQuantity)
                .orElse(java.math.BigDecimal.ZERO);
        return ProductResponse.builder()
                .id(p.getId())
                .name(p.getName())
                .sku(p.getSku())
                .barcode(p.getBarcode())
                .price(p.getPrice())
                .categoryId(p.getCategory() != null ? p.getCategory().getId() : null)
                .categoryName(p.getCategory() != null ? p.getCategory().getName() : null)
                .imageUrl(p.getImageUrl())
                .active(p.isActive())
                .quantity(qty)
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .updatedBy(p.getUpdatedBy())
                .build();
    }
}
