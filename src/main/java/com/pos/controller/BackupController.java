package com.pos.controller;

import com.pos.dto.response.ApiResponse;
import com.pos.service.BackupRestoreService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Tag(name = "Backup & Restore", description = "Full database backup and restore (JSON or SQL)")
@RestController
@RequestMapping("/api/backup")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ADMIN')")
public class BackupController {

    private final BackupRestoreService backupRestoreService;

    @GetMapping("/export/json")
    @Operation(summary = "Download JSON backup")
    public ResponseEntity<ByteArrayResource> exportJson() throws IOException {
        byte[] data = backupRestoreService.exportToJson();
        String filename = "pos-backup-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss")) + ".json";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_JSON)
                .contentLength(data.length)
                .body(new ByteArrayResource(data));
    }

    @GetMapping("/export/sql")
    @Operation(summary = "Download SQL backup (PostgreSQL)")
    public ResponseEntity<ByteArrayResource> exportSql() throws IOException {
        byte[] data = backupRestoreService.exportToSql();
        String filename = "pos-backup-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss")) + ".sql";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("application/sql"))
                .contentLength(data.length)
                .body(new ByteArrayResource(data));
    }

    @GetMapping("/sql-available")
    @Operation(summary = "Check if SQL backup is available")
    public ResponseEntity<ApiResponse<Boolean>> sqlAvailable() {
        return ResponseEntity.ok(ApiResponse.ok(backupRestoreService.isSqlBackupAvailable()));
    }

    @PostMapping(value = "/restore", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Restore from backup file (JSON or SQL)")
    public ResponseEntity<ApiResponse<String>> restore(
            @RequestParam("file") MultipartFile file,
            @RequestParam("format") String format) throws IOException {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("No file uploaded"));
        }
        byte[] data = file.getBytes();
        if ("json".equalsIgnoreCase(format)) {
            backupRestoreService.restoreFromJson(data);
        } else if ("sql".equalsIgnoreCase(format)) {
            backupRestoreService.restoreFromSql(data);
        } else {
            return ResponseEntity.badRequest().body(ApiResponse.error("Format must be json or sql"));
        }
        return ResponseEntity.ok(ApiResponse.ok("Restore completed successfully"));
    }
}
