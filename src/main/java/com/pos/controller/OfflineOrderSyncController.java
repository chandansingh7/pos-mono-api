package com.pos.controller;

import com.pos.dto.request.OfflineOrderSyncBatchRequest;
import com.pos.dto.response.ApiResponse;
import com.pos.dto.response.OfflineOrderSyncResult;
import com.pos.service.OfflineOrderSyncService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/offline-orders")
@RequiredArgsConstructor
public class OfflineOrderSyncController {

    private final OfflineOrderSyncService offlineOrderSyncService;

    @PostMapping("/sync")
    public ResponseEntity<ApiResponse<List<OfflineOrderSyncResult>>> sync(@Valid @RequestBody OfflineOrderSyncBatchRequest request) {
        List<OfflineOrderSyncResult> results = offlineOrderSyncService.syncBatch(request.getOrders());
        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.ok("Sync completed", results));
    }
}
