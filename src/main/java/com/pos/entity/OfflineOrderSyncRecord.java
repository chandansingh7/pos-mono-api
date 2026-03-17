package com.pos.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Tracks offline orders that have been synced to the server.
 * Used for idempotency: deviceId + localId uniquely identifies a sync.
 */
@Entity
@Table(name = "offline_order_sync", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"device_id", "local_id"})
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OfflineOrderSyncRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false, length = 128)
    private String deviceId;

    @Column(name = "local_id", nullable = false, length = 64)
    private String localId;

    @Column(name = "server_order_id", nullable = false)
    private Long serverOrderId;
}
