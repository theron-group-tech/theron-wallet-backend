package com.theron.wallet.service.impl;

import com.theron.wallet.dto.asaas.AsaasSubaccountRequest;
import com.theron.wallet.dto.asaas.AsaasSubaccountResponse;
import com.theron.wallet.dto.asaas.AsaasWebhookConfigRequest;
import com.theron.wallet.dto.request.CreateSubaccountRequest;
import com.theron.wallet.dto.response.SubaccountResponse;
import com.theron.wallet.entity.Subaccount;
import com.theron.wallet.entity.SubaccountApiKeyAudit;
import com.theron.wallet.enums.ApiKeyAuditAction;
import com.theron.wallet.enums.SubaccountStatus;
import com.theron.wallet.exception.AsaasApiException;
import com.theron.wallet.exception.DuplicateResourceException;
import com.theron.wallet.exception.InvalidRequestException;
import com.theron.wallet.exception.ResourceNotFoundException;
import com.theron.wallet.integration.AsaasAccountStatusClient;
import com.theron.wallet.integration.AsaasErrorBodies;
import com.theron.wallet.integration.AsaasSubaccountClient;
import com.theron.wallet.mapper.SubaccountMapper;
import com.theron.wallet.repository.SubaccountApiKeyAuditRepository;
import com.theron.wallet.repository.SubaccountRepository;
import com.theron.wallet.security.ApiKeyEncryptionService;
import com.theron.wallet.security.WebhookTokenGenerator;
import com.theron.wallet.config.AsaasProperties;
import com.theron.wallet.enums.CompanyType;
import com.theron.wallet.util.AsaasDocumentRules;
import com.theron.wallet.service.SubaccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubaccountServiceImpl implements SubaccountService {

    private static final List<String> WEBHOOK_EVENTS = List.of(
            "PAYMENT_CONFIRMED",
            "PAYMENT_RECEIVED",
            "PAYMENT_OVERDUE",
            "PAYMENT_DELETED",
            "PAYMENT_REFUNDED",
            "PAYMENT_UPDATED",
            "PAYMENT_CHARGEBACK_REQUESTED",
            "PAYMENT_CHARGEBACK_DISPUTE",
            "PAYMENT_AWAITING_CHARGEBACK_REVERSAL",
            "TRANSFER_CREATED",
            "TRANSFER_PENDING",
            "TRANSFER_IN_BANK_PROCESSING",
            "TRANSFER_BLOCKED",
            "TRANSFER_DONE",
            "TRANSFER_FAILED",
            "TRANSFER_CANCELLED"
    );

    private final SubaccountRepository subaccountRepository;
    private final SubaccountApiKeyAuditRepository auditRepository;
    private final AsaasSubaccountClient asaasSubaccountClient;
    private final AsaasAccountStatusClient asaasAccountStatusClient;
    private final ApiKeyEncryptionService encryptionService;
    private final WebhookTokenGenerator webhookTokenGenerator;
    private final AsaasProperties asaasProperties;

    @Override
    @Transactional
    public SubaccountResponse create(CreateSubaccountRequest request) {
        log.info("Creating subaccount for cpfCnpj={}", maskCpfCnpj(request.getCpfCnpj()));

        String cnpj = AsaasDocumentRules.normalize(request.getCpfCnpj());
        try {
            AsaasDocumentRules.requireCnpj(cnpj, "cpfCnpj");
        } catch (IllegalArgumentException ex) {
            throw new InvalidRequestException(ex.getMessage());
        }
        request.setCpfCnpj(cnpj);
        if (request.getCompanyType() == null) {
            request.setCompanyType(CompanyType.LIMITED);
        }

        // Phase 1: Resolve or create PROVISIONING row
        Subaccount subaccount = subaccountRepository.findByCpfCnpj(request.getCpfCnpj())
                .map(existing -> {
                    if (existing.getStatus() != SubaccountStatus.FAILED) {
                        throw new DuplicateResourceException("Subaccount", "cpfCnpj", request.getCpfCnpj());
                    }
                    log.info("Retrying FAILED subaccount: subaccountId={}, cpfCnpj={}",
                            existing.getId(), maskCpfCnpj(request.getCpfCnpj()));
                    SubaccountMapper.updateFromRequest(existing, request);
                    existing.setWebhookToken(webhookTokenGenerator.generate());
                    existing.setAsaasAccountId(null);
                    existing.setAsaasWalletId(null);
                    existing.setEncryptedApiKey(null);
                    existing.transitionTo(SubaccountStatus.PROVISIONING, "Retrying subaccount creation after previous failure");
                    return existing;
                })
                .orElseGet(() -> {
                    Subaccount newSubaccount = SubaccountMapper.toEntity(request);
                    newSubaccount.setWebhookToken(webhookTokenGenerator.generate());
                    return newSubaccount;
                });

        subaccount = subaccountRepository.save(subaccount);

        ensurePhoneFields(subaccount);

        log.info("Subaccount provisioning started: subaccountId={}, cpfCnpj={}",
                subaccount.getId(), maskCpfCnpj(request.getCpfCnpj()));

        try {
            // Phase 2: Call Asaas API — webhooks are registered inline (atomic)
            List<AsaasWebhookConfigRequest> webhooks = buildWebhookConfig(subaccount.getWebhookToken());
            AsaasSubaccountRequest asaasRequest = SubaccountMapper.toAsaasRequest(subaccount, webhooks);
            AsaasSubaccountResponse asaasResponse = asaasSubaccountClient.createSubaccount(asaasRequest);

            // Store Asaas identifiers
            subaccount.setAsaasAccountId(asaasResponse.getId());
            subaccount.setAsaasWalletId(asaasResponse.getWalletId());

            // Encrypt and store the API key (returned once by Asaas at creation time)
            if (asaasResponse.getApiKey() != null) {
                byte[] encryptedKey = encryptionService.encrypt(asaasResponse.getApiKey());
                subaccount.setEncryptedApiKey(encryptedKey);

                auditRepository.save(SubaccountApiKeyAudit.builder()
                        .subaccount(subaccount)
                        .action(ApiKeyAuditAction.CREATED)
                        .performedBy("system:provisioning")
                        .details("API key encrypted and stored during subaccount creation")
                        .build());
            } else {
                log.warn("Asaas did not return an API key for subaccount: asaasAccountId={}",
                        asaasResponse.getId());
            }

            // Transition to PENDING_EVALUATION (Asaas regulatory evaluation period)
            subaccount.transitionTo(SubaccountStatus.PENDING_EVALUATION,
                    "Subaccount created in Asaas — awaiting activation/approval");
            subaccount = subaccountRepository.save(subaccount);
            trySandboxApprove(subaccount);
            syncAsaasStatusAfterApprove(subaccount);
            subaccount = subaccountRepository.save(subaccount);

            log.info("Subaccount created successfully: subaccountId={}, asaasAccountId={}, walletId={}, status={}",
                    subaccount.getId(), asaasResponse.getId(), asaasResponse.getWalletId(), subaccount.getStatus());

        } catch (Exception ex) {
            String reason;
            if (ex instanceof AsaasApiException asaasEx) {
                log.error("Failed to create subaccount in Asaas: subaccountId={}, httpStatus={}, asaasBody={}",
                        subaccount.getId(), asaasEx.getAsaasStatusCode(), asaasEx.getAsaasErrorBody());
                reason = truncate(AsaasErrorBodies.formatFailureReason(
                        asaasEx.getAsaasStatusCode(),
                        asaasEx.getAsaasErrorBody(),
                        asaasEx.getMessage()), 400);
            } else {
                log.error("Failed to create subaccount in Asaas: subaccountId={}, error={}",
                        subaccount.getId(), ex.getMessage());
                reason = truncate("Asaas API call failed: " + ex.getMessage(), 400);
            }

            subaccount.transitionTo(SubaccountStatus.FAILED, reason);
            subaccount = subaccountRepository.save(subaccount);
        }

        return SubaccountMapper.toResponse(subaccount);
    }

    @Override
    @Transactional(readOnly = true)
    public SubaccountResponse findById(UUID subaccountId) {
        Subaccount subaccount = subaccountRepository.findById(subaccountId)
                .orElseThrow(() -> new ResourceNotFoundException("Subaccount", "id", subaccountId));
        return SubaccountMapper.toResponse(subaccount);
    }

    @Override
    @Transactional(readOnly = true)
    public SubaccountResponse findByCpfCnpj(String cpfCnpj) {
        Subaccount subaccount = subaccountRepository.findByCpfCnpj(cpfCnpj)
                .orElseThrow(() -> new ResourceNotFoundException("Subaccount", "cpfCnpj", cpfCnpj));
        return SubaccountMapper.toResponse(subaccount);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SubaccountResponse> findAll(SubaccountStatus status, Pageable pageable) {
        if (status != null) {
            return subaccountRepository.findByStatus(status, pageable)
                    .map(SubaccountMapper::toResponse);
        }
        return subaccountRepository.findAll(pageable)
                .map(SubaccountMapper::toResponse);
    }

    /**
     * Builds the webhook config list for inline registration during account creation.
     * Returns null if webhookUrl is not configured — Asaas will skip webhook setup.
     */
    private List<AsaasWebhookConfigRequest> buildWebhookConfig(String webhookToken) {
        String webhookUrl = asaasProperties.getWebhookUrl();
        if (webhookUrl == null || webhookUrl.isBlank()) {
            log.info("ASAAS_WEBHOOK_URL not set — subaccount created without inline webhook registration");
            return null;
        }
        return List.of(AsaasWebhookConfigRequest.builder()
                .name("Theron Wallet Webhook")
                .url(webhookUrl)
                .email("engineering@therongroup.com")
                .enabled(true)
                .interrupted(false)
                .apiVersion("3")
                .authToken(webhookToken)
                .sendType("SEQUENTIALLY")
                .events(WEBHOOK_EVENTS)
                .build());
    }

    private String maskCpfCnpj(String cpfCnpj) {
        if (cpfCnpj == null || cpfCnpj.length() < 4) return "****";
        return cpfCnpj.substring(0, cpfCnpj.length() - 4) + "****";
    }

    private String truncate(String value, int maxLength) {
        if (value == null) return null;
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private void ensurePhoneFields(Subaccount subaccount) {
        String mobile = subaccount.getMobilePhone();
        if (mobile != null && !mobile.isBlank()) {
            if (subaccount.getPhone() == null || subaccount.getPhone().isBlank()) {
                subaccount.setPhone(mobile);
            }
            return;
        }
        if (subaccount.getPhone() != null && !subaccount.getPhone().isBlank()) {
            subaccount.setMobilePhone(subaccount.getPhone());
        }
    }

    private void trySandboxApprove(Subaccount subaccount) {
        if (!asaasProperties.isAutoApproveSubaccounts()) {
            return;
        }
        String asaasAccountId = subaccount.getAsaasAccountId();
        if (asaasAccountId == null || asaasAccountId.isBlank()) {
            return;
        }
        try {
            asaasSubaccountClient.approveSandboxSubaccount(asaasAccountId);
            subaccount.transitionTo(SubaccountStatus.ACTIVE, "Approved in Asaas sandbox");
        } catch (Exception ex) {
            String reason;
            if (ex instanceof AsaasApiException asaasEx) {
                log.warn("Asaas sandbox approve failed: subaccountId={}, asaasAccountId={}, httpStatus={}, asaasBody={}",
                        subaccount.getId(), asaasAccountId, asaasEx.getAsaasStatusCode(), asaasEx.getAsaasErrorBody());
                reason = truncate(AsaasErrorBodies.formatFailureReason(
                        asaasEx.getAsaasStatusCode(),
                        asaasEx.getAsaasErrorBody(),
                        asaasEx.getMessage()), 400);
            } else {
                log.warn("Asaas sandbox approve failed: subaccountId={}, asaasAccountId={}, error={}",
                        subaccount.getId(), asaasAccountId, ex.getMessage());
                reason = truncate("Asaas sandbox approve failed: " + ex.getMessage(), 400);
            }
            if (subaccount.getStatus() != SubaccountStatus.PENDING_EVALUATION) {
                subaccount.transitionTo(SubaccountStatus.PENDING_EVALUATION, reason);
            } else {
                subaccount.setStatusReason(reason);
            }
        }
    }

    private void syncAsaasStatusAfterApprove(Subaccount subaccount) {
        if (subaccount.getEncryptedApiKey() == null) {
            return;
        }
        try {
            String apiKey = encryptionService.decrypt(subaccount.getEncryptedApiKey());
            var status = asaasAccountStatusClient.getStatus(apiKey);
            String commercial = status != null ? status.getCommercialInfo() : null;
            String general = status != null ? status.getGeneral() : null;
            boolean approved = (general != null && "APPROVED".equalsIgnoreCase(general))
                    || (commercial != null && "APPROVED".equalsIgnoreCase(commercial));
            if (approved) {
                subaccount.transitionTo(SubaccountStatus.ACTIVE,
                        "Asaas account status APPROVED (general=" + general + ", commercial=" + commercial + ")");
                return;
            }
            String onboardingUrl = null;
            try {
                onboardingUrl = asaasAccountStatusClient.firstOnboardingUrl(apiKey);
            } catch (Exception ignored) {
                // optional
            }
            String reason = onboardingUrl != null
                    ? "Asaas documentation pending — complete onboarding at the provided URL"
                    : "Asaas account not fully approved yet (general=" + general + ", commercial=" + commercial + ")";
            if (subaccount.getStatus() != SubaccountStatus.PENDING_EVALUATION) {
                subaccount.transitionTo(SubaccountStatus.PENDING_EVALUATION, reason);
            } else {
                subaccount.setStatusReason(reason);
            }
        } catch (Exception ex) {
            log.warn("Asaas status sync failed after approve: subaccountId={}, error={}",
                    subaccount.getId(), ex.getMessage());
        }
    }
}
