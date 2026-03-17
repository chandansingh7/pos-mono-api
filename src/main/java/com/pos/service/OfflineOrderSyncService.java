package com.pos.service;

import com.pos.dto.request.OfflineOrderSyncRequest;
import com.pos.dto.request.OrderItemRequest;
import com.pos.dto.request.OrderRequest;
import com.pos.dto.response.OfflineOrderSyncResult;
import com.pos.entity.OfflineOrderSyncRecord;
import com.pos.repository.OfflineOrderSyncRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OfflineOrderSyncService {

    private final OrderService orderService;
    private final OfflineOrderSyncRecordRepository syncRecordRepository;

    @Transactional
    public List<OfflineOrderSyncResult> syncBatch(List<OfflineOrderSyncRequest> orders) {
        List<OfflineOrderSyncResult> results = new ArrayList<>();
        for (OfflineOrderSyncRequest req : orders) {
            try {
                OfflineOrderSyncResult result = syncOne(req);
                results.add(result);
            } catch (Exception e) {
                log.warn("Offline sync failed for localId={}: {}", req.getLocalId(), e.getMessage());
                results.add(OfflineOrderSyncResult.builder()
                        .localId(req.getLocalId())
                        .status("rejected")
                        .reason(e.getMessage())
                        .build());
            }
        }
        return results;
    }

    private OfflineOrderSyncResult syncOne(OfflineOrderSyncRequest req) {
        var existing = syncRecordRepository.findByDeviceIdAndLocalId(req.getDeviceId(), req.getLocalId());
        if (existing.isPresent()) {
            log.debug("Offline order already synced: deviceId={}, localId={} -> serverOrderId={}",
                    req.getDeviceId(), req.getLocalId(), existing.get().getServerOrderId());
            return OfflineOrderSyncResult.builder()
                    .localId(req.getLocalId())
                    .serverOrderId(existing.get().getServerOrderId())
                    .status("ok")
                    .build();
        }

        OrderRequest orderReq = toOrderRequest(req);
        var response = orderService.create(orderReq);
        Long serverOrderId = response.getId();

        syncRecordRepository.save(OfflineOrderSyncRecord.builder()
                .deviceId(req.getDeviceId())
                .localId(req.getLocalId())
                .serverOrderId(serverOrderId)
                .build());

        log.info("Offline order synced: localId={} -> serverOrderId={}", req.getLocalId(), serverOrderId);
        return OfflineOrderSyncResult.builder()
                .localId(req.getLocalId())
                .serverOrderId(serverOrderId)
                .status("ok")
                .build();
    }

    private OrderRequest toOrderRequest(OfflineOrderSyncRequest req) {
        OrderRequest r = new OrderRequest();
        r.setCustomerId(req.getCustomerId());
        r.setPaymentMethod(req.getPaymentMethod());
        r.setDiscount(req.getDiscount() != null ? req.getDiscount() : java.math.BigDecimal.ZERO);
        r.setPointsToRedeem(req.getPointsToRedeem());
        r.setItems(req.getItems().stream()
                .map(i -> {
                    OrderItemRequest oir = new OrderItemRequest();
                    oir.setProductId(i.getProductId());
                    oir.setQuantity(i.getQuantity());
                    return oir;
                })
                .toList());
        return r;
    }
}
