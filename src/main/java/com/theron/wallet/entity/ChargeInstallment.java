package com.theron.wallet.entity;

import com.theron.wallet.enums.ChargeStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "charge_installment")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChargeInstallment {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "charge_id", nullable = false)
    private Charge charge;

    @Column(name = "installment_number", nullable = false)
    private Integer installmentNumber;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal value;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "asaas_payment_id", length = 50)
    private String asaasPaymentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private ChargeStatus status = ChargeStatus.PENDING;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = ChargeStatus.PENDING;
        }
    }
}
