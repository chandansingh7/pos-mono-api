package com.pos.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Per-user IP allow list (whitelist). When a user has at least one allowed IP,
 * only those IPs can be used for login and API access; otherwise all IPs are allowed.
 */
@Entity
@Table(name = "user_allowed_ip", uniqueConstraints = {
    @UniqueConstraint(columnNames = { "user_id", "ip_address" })
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAllowedIp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "ip_address", nullable = false, length = 64)
    private String ipAddress;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
