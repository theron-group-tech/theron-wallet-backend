package com.theron.wallet.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "device",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_device_user_client",
                columnNames = {"user_id", "client_device_id"}
        )
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Device {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "client_device_id", nullable = false, length = 128)
    private String clientDeviceId;

    @Column(name = "device_name", length = 255)
    private String deviceName;

    @Column(length = 64)
    private String platform;

    @Column(length = 45)
    private String ip;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "last_seen_at", nullable = false)
    @Builder.Default
    private LocalDateTime lastSeenAt = LocalDateTime.now();

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;
}
