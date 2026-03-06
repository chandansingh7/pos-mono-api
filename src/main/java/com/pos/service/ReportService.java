package com.pos.service;

import com.pos.dto.response.SalesReportResponse;
import com.pos.repository.OrderItemRepository;
import com.pos.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final OrderRepository     orderRepository;
    private final OrderItemRepository orderItemRepository;

    public SalesReportResponse getDailySummary(LocalDate date) {
        log.info("Generating daily sales report for: {}", date);
        LocalDateTime from = date.atStartOfDay();
        LocalDateTime to   = date.plusDays(1).atStartOfDay();
        return buildReport("Daily: " + date, from, to);
    }

    public SalesReportResponse getMonthlySummary(int year, int month) {
        log.info("Generating monthly sales report for: {}-{}", year, String.format("%02d", month));
        LocalDate     start = LocalDate.of(year, month, 1);
        LocalDateTime from  = start.atStartOfDay();
        LocalDateTime to    = start.plusMonths(1).atStartOfDay();
        return buildReport("Monthly: " + year + "-" + String.format("%02d", month), from, to);
    }

    /** Custom date range report. */
    public SalesReportResponse getRangeSummary(LocalDate fromDate, LocalDate toDate) {
        log.info("Generating range sales report: {} to {}", fromDate, toDate);
        LocalDateTime from = fromDate.atStartOfDay();
        LocalDateTime to   = toDate.plusDays(1).atStartOfDay();
        String period = fromDate.equals(toDate)
                ? ("Day: " + fromDate)
                : ("Range: " + fromDate + " to " + toDate);
        return buildReport(period, from, to);
    }

    private SalesReportResponse buildReport(String period, LocalDateTime from, LocalDateTime to) {
        long       totalOrders  = orderRepository.countCompletedBetween(from, to);
        BigDecimal totalRevenue = orderRepository.sumTotalBetween(from, to);
        if (totalRevenue == null) totalRevenue = BigDecimal.ZERO;

        BigDecimal avgOrder = totalOrders > 0
                ? totalRevenue.divide(BigDecimal.valueOf(totalOrders), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        List<Object[]> rawTop = orderItemRepository.findTopProductsBetween(from, to, PageRequest.of(0, 5));
        List<SalesReportResponse.TopProductEntry> topProducts = rawTop.stream()
                .map(row -> SalesReportResponse.TopProductEntry.builder()
                        .productId((Long) row[0])
                        .productName((String) row[1])
                        .unitsSold(((Number) row[2]).longValue())
                        .build())
                .collect(Collectors.toList());

        log.info("Report '{}': orders={}, revenue={}, avgOrder={}",
                period, totalOrders, totalRevenue, avgOrder);

        return SalesReportResponse.builder()
                .period(period)
                .totalOrders(totalOrders)
                .totalRevenue(totalRevenue)
                .averageOrderValue(avgOrder)
                .topProducts(topProducts)
                .build();
    }

    public byte[] exportDailyToExcel(LocalDate date) {
        SalesReportResponse report = getDailySummary(date);
        return buildReportExcel("Daily Sales - " + date, report);
    }

    public byte[] exportMonthlyToExcel(int year, int month) {
        SalesReportResponse report = getMonthlySummary(year, month);
        String title = year + "-" + String.format("%02d", month);
        return buildReportExcel("Monthly Sales - " + title, report);
    }

    public byte[] exportRangeToExcel(LocalDate fromDate, LocalDate toDate) {
        SalesReportResponse report = getRangeSummary(fromDate, toDate);
        String title = fromDate + "_to_" + toDate;
        return buildReportExcel("Sales Range - " + title, report);
    }

    private byte[] buildReportExcel(String sheetName, SalesReportResponse report) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet(sanitizeSheetName(sheetName));
            int rowNum = 0;

            CellStyle headerStyle = wb.createCellStyle();
            Font headerFont = wb.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            Row headerRow = sheet.createRow(rowNum++);
            createCell(headerRow, 0, "Period", headerStyle);
            createCell(headerRow, 1, report.getPeriod(), null);
            rowNum++;

            Row r2 = sheet.createRow(rowNum++);
            createCell(r2, 0, "Total Orders", headerStyle);
            createCell(r2, 1, String.valueOf(report.getTotalOrders()), null);
            Row r3 = sheet.createRow(rowNum++);
            createCell(r3, 0, "Total Revenue", headerStyle);
            createCell(r3, 1, report.getTotalRevenue() != null ? report.getTotalRevenue().toString() : "0", null);
            Row r4 = sheet.createRow(rowNum++);
            createCell(r4, 0, "Average Order Value", headerStyle);
            createCell(r4, 1, report.getAverageOrderValue() != null ? report.getAverageOrderValue().toString() : "0", null);
            rowNum++;

            Row topHeader = sheet.createRow(rowNum++);
            createCell(topHeader, 0, "Top Products", headerStyle);
            Row colHeader = sheet.createRow(rowNum++);
            createCell(colHeader, 0, "Product", headerStyle);
            createCell(colHeader, 1, "Units Sold", headerStyle);

            List<SalesReportResponse.TopProductEntry> top = report.getTopProducts() != null ? report.getTopProducts() : List.of();
            for (SalesReportResponse.TopProductEntry e : top) {
                Row row = sheet.createRow(rowNum++);
                createCell(row, 0, e.getProductName(), null);
                createCell(row, 1, String.valueOf(e.getUnitsSold()), null);
            }

            sheet.autoSizeColumn(0);
            sheet.autoSizeColumn(1);
            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Excel export failed", e);
            throw new RuntimeException("Failed to generate Excel report", e);
        }
    }

    private static void createCell(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value != null ? value : "");
        if (style != null) cell.setCellStyle(style);
    }

    private static String sanitizeSheetName(String name) {
        if (name == null) return "Report";
        return name.replaceAll("[\\\\/:*?\\[\\]]", "_").substring(0, Math.min(31, name.length()));
    }
}
