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
import com.theron.wallet.exception.ResourceNotFoundException;
import com.theron.wallet.integration.AsaasErrorBodies;
import com.theron.wallet.integration.AsaasSubaccountClient;
import com.theron.wallet.mapper.SubaccountMapper;
import com.theron.wallet.repository.SubaccountApiKeyAuditRepository;
import com.theron.wallet.repository.SubaccountRepository;
import com.theron.wallet.security.ApiKeyEncryptionService;
import com.theron.wallet.security.WebhookTokenGenerator;
import com.theron.wallet.config.AsaasProperties;
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
    private final ApiKeyEncryptionService encryptionService;
    private final WebhookTokenGenerator webhookTokenGenerator;
    private final AsaasProperties asaasProperties;

    @Override
    @Transactional
    public SubaccountResponse create(CreateSubaccountRequest request) {
        log.info("Creating subaccount for cpfCnpj={}", maskCpfCnpj(request.getCpfCnpj()));

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
                    "Subaccount created in Asaas — awaiting regulatory evaluation");
            subaccount = subaccountRepository.save(subaccount);

            log.info("Subaccount created successfully: subaccountId={}, asaasAccountId={}, walletId={}",
                    subaccount.getId(), asaasResponse.getId(), asaasResponse.getWalletId());

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
            log.warn("Webhook URL not configured — subaccount will be created without inline webhook registration");
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
}
