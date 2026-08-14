package com.theron.wallet.entity;

import com.theron.wallet.enums.SubaccountStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PreUpdate;
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
@Table(name = "subaccount")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Subaccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Theron Account this Asaas subaccount rails. Null for legacy standalone subaccounts. */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", unique = true)
    private Account account;

    // ── Subaccount owner identity ─────────────────────────────────────────────
    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(name = "login_email", length = 255)
    private String loginEmail;

    @Column(name = "cpf_cnpj", nullable = false, unique = true, length = 20)
    private String cpfCnpj;

    @Column(name = "mobile_phone", nullable = false, length = 20)
    private String mobilePhone;

    @Column(length = 20)
    private String phone;

    @Column(length = 500)
    private String site;

    // ── Asaas integration ────────────────────────────────────────────────────
    @Column(name = "asaas_account_id", unique = true, length = 50)
    private String asaasAccountId;

    @Column(name = "asaas_wallet_id", unique = true, length = 50)
    private String asaasWalletId;

    /** ID do cliente criado no Asaas via POST /v3/customers (usado como "customer" nas cobranças Pix) */
    @Column(name = "asaas_customer_id", unique = true, length = 50)
    private String asaasCustomerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private SubaccountStatus status = SubaccountStatus.PROVISIONING;

    @Column(name = "encrypted_api_key")
    private byte[] encryptedApiKey;

    @Column(name = "webhook_token", length = 128)
    private String webhookToken;

    // ── Financials & address ─────────────────────────────────────────────────
    @Column(name = "income_value", nullable = false, precision = 19, scale = 2)
    private BigDecimal incomeValue;

    @Column(nullable = false, length = 255)
    private String address;

    @Column(name = "address_number", nullable = false, length = 20)
    private String addressNumber;

    @Column(length = 100)
    private String complement;

    @Column(nullable = false, length = 100)
    private String province;

    @Column(name = "postal_code", nullable = false, length = 10)
    private String postalCode;

    @Column(name = "birth_date", length = 10)
    private String birthDate;

    @Column(name = "company_type", length = 50)
    private String companyType;

    // ── Audit ────────────────────────────────────────────────────────────────
    @Column(name = "status_reason", length = 500)
    private String statusReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public void transitionTo(SubaccountStatus newStatus, String reason) {
        this.status = newStatus;
        this.statusReason = reason;
    }
}
