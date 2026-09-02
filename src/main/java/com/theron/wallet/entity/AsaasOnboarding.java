package com.theron.wallet.entity;

import com.theron.wallet.enums.AsaasOnboardingStatus;
import com.theron.wallet.enums.AsaasOnboardingStep;
import com.theron.wallet.enums.AsaasPersonType;
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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "asaas_onboarding")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AsaasOnboarding {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, unique = true)
    private Account account;

    @Enumerated(EnumType.STRING)
    @Column(name = "person_type", length = 20)
    private AsaasPersonType personType;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_step", nullable = false, length = 40)
    @Builder.Default
    private AsaasOnboardingStep currentStep = AsaasOnboardingStep.ACCOUNT_TYPE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    @Builder.Default
    private AsaasOnboardingStatus status = AsaasOnboardingStatus.NOT_STARTED;

    @Column(name = "payload_json", columnDefinition = "TEXT")
    private String payloadJson;

    @Column(name = "last_error_code", length = 80)
    private String lastErrorCode;

    @Column(name = "last_error_message", length = 500)
    private String lastErrorMessage;

    @Column(name = "idempotency_key", length = 64)
    private String idempotencyKey;

    @Column(name = "submit_attempts", nullable = false)
    @Builder.Default
    private int submitAttempts = 0;

    @Column(name = "onboarding_url", length = 1024)
    private String onboardingUrl;

    @Column(name = "asaas_account_id", length = 50)
    private String asaasAccountId;

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
}
