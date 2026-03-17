package com.pos.repository;

import com.pos.entity.OfflineOrderSyncRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OfflineOrderSyncRecordRepository extends JpaRepository<OfflineOrderSyncRecord, Long> {

    Optional<OfflineOrderSyncRecord> findByDeviceIdAndLocalId(String deviceId, String localId);
}
