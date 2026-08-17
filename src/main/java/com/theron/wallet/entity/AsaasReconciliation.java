package com.theron.wallet.entity;

import com.theron.wallet.enums.ReconciliationKind;
import com.theron.wallet.enums.ReconciliationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "asaas_reconciliation")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AsaasReconciliation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id")
    private Transaction transaction;

    @Column(name = "asaas_id", nullable = false, length = 80)
    private String asaasId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReconciliationKind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReconciliationStatus status;

    @Column(name = "local_status", length = 40)
    private String localStatus;

    @Column(name = "asaas_status", length = 40)
    private String asaasStatus;

    @Column(name = "local_amount", precision = 19, scale = 2)
    private BigDecimal localAmount;

    @Column(name = "asaas_amount", precision = 19, scale = 2)
    private BigDecimal asaasAmount;

    @Column(length = 500)
    private String detail;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
