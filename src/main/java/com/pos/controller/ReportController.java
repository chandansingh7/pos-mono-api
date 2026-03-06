package com.pos.controller;

import com.pos.dto.response.ApiResponse;
import com.pos.dto.response.SalesReportResponse;
import com.pos.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('ADMIN', 'MANAGER')")
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/sales/daily")
    public ResponseEntity<ApiResponse<SalesReportResponse>> dailySales(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate target = date != null ? date : LocalDate.now();
        return ResponseEntity.ok(ApiResponse.ok(reportService.getDailySummary(target)));
    }

    @GetMapping("/sales/monthly")
    public ResponseEntity<ApiResponse<SalesReportResponse>> monthlySales(
            @RequestParam(defaultValue = "#{T(java.time.LocalDate).now().getYear()}") int year,
            @RequestParam(defaultValue = "#{T(java.time.LocalDate).now().getMonthValue()}") int month) {
        return ResponseEntity.ok(ApiResponse.ok(reportService.getMonthlySummary(year, month)));
    }

    @GetMapping("/sales/range")
    public ResponseEntity<ApiResponse<SalesReportResponse>> rangeSales(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        if (!from.isBefore(to) && !from.equals(to)) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Invalid range: from must be before or equal to to"));
        }
        return ResponseEntity.ok(ApiResponse.ok(reportService.getRangeSummary(from, to)));
    }

    @GetMapping(value = "/sales/daily/export", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public ResponseEntity<byte[]> exportDailyExcel(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate target = date != null ? date : LocalDate.now();
        byte[] body = reportService.exportDailyToExcel(target);
        String filename = "sales-daily-" + target + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .contentLength(body.length)
                .body(body);
    }

    @GetMapping(value = "/sales/monthly/export", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public ResponseEntity<byte[]> exportMonthlyExcel(
            @RequestParam(defaultValue = "#{T(java.time.LocalDate).now().getYear()}") int year,
            @RequestParam(defaultValue = "#{T(java.time.LocalDate).now().getMonthValue()}") int month) {
        byte[] body = reportService.exportMonthlyToExcel(year, month);
        String filename = "sales-monthly-" + year + "-" + String.format("%02d", month) + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .contentLength(body.length)
                .body(body);
    }

    @GetMapping(value = "/sales/range/export", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public ResponseEntity<byte[]> exportRangeExcel(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        if (!from.isBefore(to) && !from.equals(to)) {
            return ResponseEntity.badRequest().build();
        }
        byte[] body = reportService.exportRangeToExcel(from, to);
        String filename = "sales-range-" + from + "-to-" + to + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .contentLength(body.length)
                .body(body);
    }
}
