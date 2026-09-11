package com.theron.wallet.entity;

import com.theron.wallet.enums.ChargeSplitRole;
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
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "charge_split")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChargeSplit {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "charge_id", nullable = false)
    private Charge charge;

    @Column(name = "wallet_id", nullable = false, length = 100)
    private String walletId;

    @Column(name = "percentual_value", precision = 10, scale = 4)
    private BigDecimal percentualValue;

    @Column(name = "fixed_value", precision = 19, scale = 2)
    private BigDecimal fixedValue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChargeSplitRole role;

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
    }
}
