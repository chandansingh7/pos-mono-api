package com.pos.service;

import com.pos.dto.response.BulkUploadResult;
import com.pos.entity.Category;
import com.pos.entity.Inventory;
import com.pos.entity.Product;
import com.pos.repository.CategoryRepository;
import com.pos.repository.InventoryRepository;
import com.pos.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductBulkService {

    private static final int HEADER_ROW = 0;
    private static final int COL_NAME = 0;
    private static final int COL_SKU = 1;
    private static final int COL_BARCODE = 2;
    private static final int COL_PRICE = 3;
    private static final int COL_CATEGORY = 4;
    private static final int COL_INITIAL_STOCK = 5;
    private static final int COL_LOW_STOCK_THRESHOLD = 6;

    private final ProductRepository   productRepository;
    private final CategoryRepository  categoryRepository;
    private final InventoryRepository inventoryRepository;

    @Transactional
    public BulkUploadResult processUpload(MultipartFile file, String updatedBy) {
        String name = file.getOriginalFilename();
        boolean isCsv = name != null && name.toLowerCase().endsWith(".csv");
        log.info("Bulk upload started: file={}, size={} bytes, format={}, updatedBy={}",
                name, file.getSize(), isCsv ? "CSV" : "Excel", updatedBy);

        try (InputStream is = file.getInputStream()) {
            BulkUploadResult result = isCsv ? processCsv(is, updatedBy) : processExcel(is, updatedBy);
            log.info("Bulk upload finished: totalRows={}, successCount={}, updatedCount={}, failCount={}",
                    result.getTotalRows(), result.getSuccessCount(), result.getUpdatedCount(), result.getFailCount());
            return result;
        } catch (Exception e) {
            log.error("Bulk upload failed", e);
            return BulkUploadResult.builder()
                    .totalRows(0)
                    .successCount(0)
                    .failCount(1)
                    .errors(List.of(BulkUploadResult.RowError.builder()
                            .row(0)
                            .field("file")
                            .message("Failed to read file: " + e.getMessage())
                            .build()))
                    .build();
        }
    }

    private BulkUploadResult processExcel(InputStream is, String updatedBy) throws Exception {
        List<BulkUploadResult.RowError> errors = new ArrayList<>();
        List<RowResult> toUpdate = new ArrayList<>();
        List<RowResult> toCreate = new ArrayList<>();

        Workbook workbook = WorkbookFactory.create(is);
        Sheet sheet = workbook.getSheetAt(0);
        int lastRow = sheet.getLastRowNum();
        workbook.close();

        if (lastRow < 1) {
            return BulkUploadResult.builder()
                    .totalRows(0)
                    .successCount(0)
                    .updatedCount(0)
                    .failCount(1)
                    .errors(List.of(BulkUploadResult.RowError.builder()
                            .row(1)
                            .field("file")
                            .message("No data rows. Use row 1 for headers, data from row 2.")
                            .build()))
                    .build();
        }

        int totalRows = lastRow;
        log.info("Bulk upload Excel: parsing {} data rows", lastRow);
        for (int r = 1; r <= lastRow; r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            if (!excelRowHasAnyValue(row)) continue;

            RowResult rowResult = mapRowToProduct(row, r + 1, errors, updatedBy);
            if (rowResult != null) {
                if (rowResult.isUpdate() && rowResult.existingInventory() != null) {
                    toUpdate.add(rowResult);
                } else {
                    toCreate.add(rowResult);
                }
            }
        }
        log.info("Bulk upload Excel: parsed toUpdate={}, toCreate={}, mappingErrors={}",
                toUpdate.size(), toCreate.size(), errors.size());

        return flushBatches(toUpdate, toCreate, totalRows, errors);
    }

    private static boolean excelRowHasAnyValue(Row row) {
        for (int c = 0; c <= COL_LOW_STOCK_THRESHOLD; c++) {
            String v = getCellString(row.getCell(c));
            if (v != null && !v.isBlank()) return true;
        }
        return false;
    }

    private static final int SAVE_BATCH_SIZE = 50;

    private BulkUploadResult processCsv(InputStream is, String updatedBy) {
        List<BulkUploadResult.RowError> errors = new ArrayList<>();
        List<RowResult> toUpdate = new ArrayList<>();
        List<RowResult> toCreate = new ArrayList<>();
        int totalRows = 0;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            int rowNum = 0;
            reader.readLine(); // skip header
            while ((line = reader.readLine()) != null) {
                rowNum++;
                if (line.isBlank()) continue;
                String[] cells = parseCsvLine(line);
                if (cells.length == 0) continue;
                if (!rowHasAnyValue(cells)) continue;

                RowResult rowResult = mapCsvRowToProduct(cells, rowNum + 1, errors, updatedBy);
                if (rowResult != null) {
                    if (rowResult.isUpdate() && rowResult.existingInventory() != null) {
                        toUpdate.add(rowResult);
                    } else {
                        toCreate.add(rowResult);
                    }
                }
                totalRows = rowNum;
            }
            log.info("Bulk upload CSV: parsed totalRows={}, toUpdate={}, toCreate={}, mappingErrors={}",
                    totalRows, toUpdate.size(), toCreate.size(), errors.size());
        } catch (Exception e) {
            log.error("CSV parse failed", e);
            errors.add(BulkUploadResult.RowError.builder()
                    .row(0)
                    .field("file")
                    .message("Failed to read CSV: " + e.getMessage())
                    .build());
            return BulkUploadResult.builder()
                    .totalRows(totalRows)
                    .successCount(0)
                    .updatedCount(0)
                    .failCount(errors.size())
                    .errors(errors)
                    .build();
        }

        return flushBatches(toUpdate, toCreate, totalRows, errors);
    }

    private static boolean rowHasAnyValue(String[] cells) {
        for (String c : cells) {
            if (c != null && !c.isBlank()) return true;
        }
        return false;
    }

    private BulkUploadResult flushBatches(List<RowResult> toUpdate, List<RowResult> toCreate,
                                          int totalRows, List<BulkUploadResult.RowError> errors) {
        log.info("Bulk upload flush: toUpdate={}, toCreate={}, batchSize={}", toUpdate.size(), toCreate.size(), SAVE_BATCH_SIZE);
        if (!toUpdate.isEmpty()) {
            String sampleUpdateSkus = toUpdate.stream().map(RowResult::product).limit(5)
                    .map(p -> p.getSku() != null ? p.getSku() : "(no SKU)").toList().toString();
            log.info("Bulk upload saving updates (sample SKUs): {}", sampleUpdateSkus);
        }
        if (!toCreate.isEmpty()) {
            String sampleCreateSkus = toCreate.stream().map(RowResult::product).limit(5)
                    .map(p -> p.getSku() != null ? p.getSku() : "(no SKU)").toList().toString();
            log.info("Bulk upload saving new products (sample SKUs): {}", sampleCreateSkus);
        }
        try {
            for (int i = 0; i < toUpdate.size(); i += SAVE_BATCH_SIZE) {
                int end = Math.min(i + SAVE_BATCH_SIZE, toUpdate.size());
                List<RowResult> chunk = toUpdate.subList(i, end);
                List<Product> products = chunk.stream().map(RowResult::product).toList();
                List<Inventory> inventories = chunk.stream().map(RowResult::existingInventory).toList();
                productRepository.saveAll(products);
                inventoryRepository.saveAll(inventories);
                log.debug("Bulk upload: saved update batch rows {}-{} ({} products, {} inventories)", i + 1, end, products.size(), inventories.size());
            }
            for (int i = 0; i < toCreate.size(); i += SAVE_BATCH_SIZE) {
                int end = Math.min(i + SAVE_BATCH_SIZE, toCreate.size());
                List<RowResult> createChunk = toCreate.subList(i, end);
                List<Product> newProducts = createChunk.stream().map(RowResult::product).toList();
                List<Product> saved = productRepository.saveAll(newProducts);
                List<Inventory> newInventories = new ArrayList<>(saved.size());
                for (int j = 0; j < saved.size(); j++) {
                    RowResult r = createChunk.get(j);
                    newInventories.add(Inventory.builder()
                            .product(saved.get(j))
                            .quantity(BigDecimal.valueOf(r.initialStock()))
                            .lowStockThreshold(r.lowStockThreshold())
                            .build());
                }
                inventoryRepository.saveAll(newInventories);
                log.debug("Bulk upload: saved create batch rows {}-{} ({} products, {} inventories)", i + 1, end, saved.size(), newInventories.size());
            }
            log.info("Bulk upload flush done: saved {} updates, {} creates", toUpdate.size(), toCreate.size());
        } catch (Exception e) {
            log.warn("Bulk save failed: {}", e.getMessage());
            errors.add(BulkUploadResult.RowError.builder()
                    .row(0)
                    .field("save")
                    .message("Bulk save failed: " + e.getMessage())
                    .build());
        }

        return BulkUploadResult.builder()
                .totalRows(totalRows)
                .successCount(toCreate.size())
                .updatedCount(toUpdate.size())
                .failCount(errors.size())
                .errors(errors)
                .build();
    }

    private static String[] parseCsvLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                out.add(cur.toString().trim().replace("\"\"", "\""));
                cur = new StringBuilder();
            } else if (c == '\n' || c == '\r') {
                break;
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString().trim().replace("\"\"", "\""));
        return out.toArray(new String[0]);
    }

    private static String cellAt(String[] cells, int index) {
        if (index >= cells.length) return null;
        String s = cells[index];
        return s == null || s.isBlank() ? null : s.trim();
    }

    private RowResult mapCsvRowToProduct(String[] cells, int rowNum, List<BulkUploadResult.RowError> errors, String updatedBy) {
        String nameCell = cellAt(cells, COL_NAME);
        String sku = cellAt(cells, COL_SKU);
        String barcodeCell = cellAt(cells, COL_BARCODE);
        String priceCell = cellAt(cells, COL_PRICE);
        BigDecimal price = parseBigDecimal(priceCell);
        Category category = resolveCategory(cellAt(cells, COL_CATEGORY));
        int initialStock = parseInt(cellAt(cells, COL_INITIAL_STOCK), 0);
        int lowStockThreshold = parseInt(cellAt(cells, COL_LOW_STOCK_THRESHOLD), 10);

        // If SKU exists, treat row as an update: add quantity and only override fields that are supplied.
        if (sku != null && !sku.isBlank()) {
            Optional<Product> existingOpt = productRepository.findBySku(sku.trim());
            if (existingOpt.isPresent()) {
                Product existing = existingOpt.get();

                if (nameCell != null && !nameCell.isBlank()) {
                    existing.setName(nameCell.trim());
                }
                if (barcodeCell != null && !barcodeCell.isBlank()) {
                    existing.setBarcode(barcodeCell.trim());
                }
                if (priceCell != null && !priceCell.isBlank()) {
                    if (price == null || price.compareTo(BigDecimal.ZERO) < 0) {
                        errors.add(BulkUploadResult.RowError.builder()
                                .row(rowNum)
                                .field("Price")
                                .message("Invalid price")
                                .build());
                        return null;
                    }
                    existing.setPrice(price);
                }
                if (category != null) {
                    existing.setCategory(category);
                }
                existing.setUpdatedBy(updatedBy);
                existing.setUpdatedAt(LocalDateTime.now());

                Optional<Inventory> invOpt = inventoryRepository.findByProductId(existing.getId());
                Inventory inv = invOpt.orElseGet(() -> Inventory.builder()
                        .product(existing)
                        .quantity(BigDecimal.ZERO)
                        .lowStockThreshold(lowStockThreshold)
                        .updatedBy(updatedBy)
                        .build());

                inv.setQuantity(inv.getQuantity().add(BigDecimal.valueOf(initialStock)));
                inv.setLowStockThreshold(lowStockThreshold);
                inv.setUpdatedBy(updatedBy);

                return new RowResult(existing, initialStock, lowStockThreshold, true, inv);
            }
        }

        // New product path: require name and a valid price.
        if (nameCell == null || nameCell.isBlank()) return null;
        if (price == null || price.compareTo(BigDecimal.ZERO) < 0) {
            errors.add(BulkUploadResult.RowError.builder()
                    .row(rowNum)
                    .field("Price")
                    .message("Invalid or missing price")
                    .build());
            return null;
        }

        if (barcodeCell != null && !barcodeCell.isBlank() && productRepository.existsByBarcode(barcodeCell)) {
            errors.add(BulkUploadResult.RowError.builder()
                    .row(rowNum)
                    .field("Barcode")
                    .message("Barcode already exists: " + barcodeCell)
                    .build());
            return null;
        }

        Product product = Product.builder()
                .name(nameCell.trim())
                .sku(sku != null && !sku.isBlank() ? sku.trim() : null)
                .barcode(barcodeCell != null && !barcodeCell.isBlank() ? barcodeCell.trim() : null)
                .price(price)
                .category(category)
                .active(true)
                .updatedBy(updatedBy)
                .build();
        return new RowResult(product, initialStock, lowStockThreshold);
    }

    private static BigDecimal parseBigDecimal(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return new BigDecimal(s.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static int parseInt(String s, int defaultValue) {
        if (s == null || s.isBlank()) return defaultValue;
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private RowResult mapRowToProduct(Row row, int rowNum, List<BulkUploadResult.RowError> errors, String updatedBy) {
        String nameCell = getCellString(row.getCell(COL_NAME));
        String sku = getCellString(row.getCell(COL_SKU));
        String barcodeCell = getCellString(row.getCell(COL_BARCODE));
        BigDecimal price = getCellBigDecimal(row.getCell(COL_PRICE));
        Category category = resolveCategory(getCellString(row.getCell(COL_CATEGORY)));
        int initialStock = getCellInt(row.getCell(COL_INITIAL_STOCK), 0);
        int lowStockThreshold = getCellInt(row.getCell(COL_LOW_STOCK_THRESHOLD), 10);

        // If SKU exists, treat row as an update: add quantity and only override fields that are supplied.
        if (sku != null && !sku.isBlank()) {
            Optional<Product> existingOpt = productRepository.findBySku(sku.trim());
            if (existingOpt.isPresent()) {
                Product existing = existingOpt.get();

                if (nameCell != null && !nameCell.isBlank()) {
                    existing.setName(nameCell.trim());
                }
                if (barcodeCell != null && !barcodeCell.isBlank()) {
                    existing.setBarcode(barcodeCell.trim());
                }
                if (getCellString(row.getCell(COL_PRICE)) != null && !getCellString(row.getCell(COL_PRICE)).isBlank()) {
                    if (price == null || price.compareTo(BigDecimal.ZERO) < 0) {
                        errors.add(BulkUploadResult.RowError.builder()
                                .row(rowNum)
                                .field("Price")
                                .message("Invalid price")
                                .build());
                        return null;
                    }
                    existing.setPrice(price);
                }
                if (category != null) {
                    existing.setCategory(category);
                }
                existing.setUpdatedBy(updatedBy);
                existing.setUpdatedAt(LocalDateTime.now());

                Optional<Inventory> invOpt = inventoryRepository.findByProductId(existing.getId());
                Inventory inv = invOpt.orElseGet(() -> Inventory.builder()
                        .product(existing)
                        .quantity(BigDecimal.ZERO)
                        .lowStockThreshold(lowStockThreshold)
                        .updatedBy(updatedBy)
                        .build());

                inv.setQuantity(inv.getQuantity().add(BigDecimal.valueOf(initialStock)));
                inv.setLowStockThreshold(lowStockThreshold);
                inv.setUpdatedBy(updatedBy);

                return new RowResult(existing, initialStock, lowStockThreshold, true, inv);
            }
        }

        // New product path: require name and a valid price.
        if (nameCell == null || nameCell.isBlank()) return null;
        if (price == null || price.compareTo(BigDecimal.ZERO) < 0) {
            errors.add(BulkUploadResult.RowError.builder()
                    .row(rowNum)
                    .field("Price")
                    .message("Invalid or missing price")
                    .build());
            return null;
        }

        if (barcodeCell != null && !barcodeCell.isBlank() && productRepository.existsByBarcode(barcodeCell)) {
            errors.add(BulkUploadResult.RowError.builder()
                    .row(rowNum)
                    .field("Barcode")
                    .message("Barcode already exists: " + barcodeCell)
                    .build());
            return null;
        }

        Product product = Product.builder()
                .name(nameCell.trim())
                .sku(sku != null && !sku.isBlank() ? sku.trim() : null)
                .barcode(barcodeCell != null && !barcodeCell.isBlank() ? barcodeCell.trim() : null)
                .price(price)
                .category(category)
                .active(true)
                .updatedBy(updatedBy)
                .build();

        return new RowResult(product, initialStock, lowStockThreshold);
    }

    private Category resolveCategory(String categoryStr) {
        if (categoryStr == null || categoryStr.isBlank()) return null;
        categoryStr = categoryStr.trim();
        Optional<Category> byName = categoryRepository.findByName(categoryStr);
        if (byName.isPresent()) return byName.get();
        try {
            Long id = Long.parseLong(categoryStr);
            return categoryRepository.findById(id).orElse(null);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String getCellString(Cell cell) {
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCellFormula();
            default -> null;
        };
    }

    private static BigDecimal getCellBigDecimal(Cell cell) {
        if (cell == null) return null;
        try {
            if (cell.getCellType() == CellType.NUMERIC) {
                return BigDecimal.valueOf(cell.getNumericCellValue());
            }
            if (cell.getCellType() == CellType.STRING) {
                String s = cell.getStringCellValue();
                if (s == null || s.isBlank()) return null;
                return new BigDecimal(s.trim());
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    private static int getCellInt(Cell cell, int defaultValue) {
        if (cell == null) return defaultValue;
        try {
            if (cell.getCellType() == CellType.NUMERIC) {
                return (int) cell.getNumericCellValue();
            }
            if (cell.getCellType() == CellType.STRING) {
                String s = cell.getStringCellValue();
                if (s == null || s.isBlank()) return defaultValue;
                return Integer.parseInt(s.trim());
            }
        } catch (Exception e) {
            return defaultValue;
        }
        return defaultValue;
    }

    public byte[] generateExcelTemplate() {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Products");
            Row header = sheet.createRow(0);
            String[] headers = { "Name", "SKU", "Barcode", "Price", "Category", "Initial Stock", "Low Stock Threshold" };
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }
            Row exampleRow = sheet.createRow(1);
            exampleRow.createCell(0).setCellValue("Example Product");
            exampleRow.createCell(1).setCellValue("SKU-001");
            exampleRow.createCell(2).setCellValue("1234567890123");
            exampleRow.createCell(3).setCellValue(9.99);
            exampleRow.createCell(4).setCellValue("Electronics");
            exampleRow.createCell(5).setCellValue(100);
            exampleRow.createCell(6).setCellValue(10);
            for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);
            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate template", e);
        }
    }

    public byte[] generateCsvTemplate() {
        String header = "Name,SKU,Barcode,Price,Category,Initial Stock,Low Stock Threshold";
        String example = "Example Product,SKU-001,1234567890123,9.99,Electronics,100,10";
        return (header + "\n" + example).getBytes(StandardCharsets.UTF_8);
    }

    /** Returns which of the given SKUs already exist (non-null, non-blank only). */
    public List<String> findExistingSkus(List<String> skus) {
        if (skus == null || skus.isEmpty()) return List.of();
        List<String> toCheck = skus.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(String::trim)
                .distinct()
                .collect(Collectors.toList());
        if (toCheck.isEmpty()) return List.of();
        return productRepository.findSkusBySkuIn(toCheck);
    }

    private record RowResult(Product product, int initialStock, int lowStockThreshold, boolean isUpdate, Inventory existingInventory) {
        RowResult(Product product, int initialStock, int lowStockThreshold) {
            this(product, initialStock, lowStockThreshold, false, null);
        }
    }
}
