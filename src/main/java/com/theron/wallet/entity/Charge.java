package com.theron.wallet.entity;

import com.theron.wallet.enums.ChargeBillingType;
import com.theron.wallet.enums.ChargeStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "charge")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Charge {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subaccount_id")
    private Subaccount subaccount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "billing_customer_id", nullable = false)
    private BillingCustomer billingCustomer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id")
    private Transaction transaction;

    @Column(name = "asaas_payment_id", length = 50)
    private String asaasPaymentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_type", nullable = false, length = 20)
    private ChargeBillingType billingType;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal value;

    @Column(name = "net_value", precision = 19, scale = 2)
    private BigDecimal netValue;

    @Column(length = 500)
    private String description;

    @Column(name = "external_reference", length = 100)
    private String externalReference;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private ChargeStatus status = ChargeStatus.PENDING;

    @Column(name = "installment_count", nullable = false)
    @Builder.Default
    private Integer installmentCount = 1;

    @Column(name = "invoice_url", length = 500)
    private String invoiceUrl;

    @Column(name = "bank_slip_url", length = 500)
    private String bankSlipUrl;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "charge", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ChargeInstallment> installments = new ArrayList<>();

    @OneToMany(mappedBy = "charge", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ChargeSplit> splits = new ArrayList<>();

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (status == null) {
            status = ChargeStatus.PENDING;
        }
        if (installmentCount == null) {
            installmentCount = 1;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
