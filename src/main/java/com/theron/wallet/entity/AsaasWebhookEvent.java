package com.theron.wallet.entity;

import com.theron.wallet.enums.AsaasWebhookEventStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "asaas_webhook_event")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AsaasWebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "asaas_event_id", nullable = false, unique = true, length = 120)
    private String asaasEventId;

    @Column(nullable = false, length = 80)
    private String event;

    @Column(name = "resource_id", length = 80)
    private String resourceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AsaasWebhookEventStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "processed_at")
    private LocalDateTime processedAt;
}
