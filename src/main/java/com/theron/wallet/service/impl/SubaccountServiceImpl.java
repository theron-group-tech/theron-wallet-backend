package com.theron.wallet.service.impl;

import com.theron.wallet.dto.asaas.AsaasSubaccountRequest;
import com.theron.wallet.dto.asaas.AsaasSubaccountResponse;
import com.theron.wallet.dto.asaas.AsaasWebhookConfigRequest;
import com.theron.wallet.dto.request.CreateSubaccountRequest;
import com.theron.wallet.dto.response.SubaccountResponse;
import com.theron.wallet.entity.Customer;
import com.theron.wallet.entity.Subaccount;
import com.theron.wallet.entity.SubaccountApiKeyAudit;
import com.theron.wallet.enums.ApiKeyAuditAction;
import com.theron.wallet.enums.SubaccountStatus;
import com.theron.wallet.exception.DuplicateResourceException;
import com.theron.wallet.exception.ResourceNotFoundException;
import com.theron.wallet.exception.SubaccountOperationBlockedException;
import com.theron.wallet.integration.AsaasSubaccountClient;
import com.theron.wallet.integration.AsaasWebhookClient;
import com.theron.wallet.mapper.SubaccountMapper;
import com.theron.wallet.repository.CustomerRepository;
import com.theron.wallet.repository.SubaccountApiKeyAuditRepository;
import com.theron.wallet.repository.SubaccountRepository;
import com.theron.wallet.security.ApiKeyEncryptionService;
import com.theron.wallet.security.WebhookTokenGenerator;
import com.theron.wallet.config.AsaasProperties;
import com.theron.wallet.service.SubaccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubaccountServiceImpl implements SubaccountService {

    private static final List<String> PAYMENT_WEBHOOK_EVENTS = List.of(
            "PAYMENT_CONFIRMED",
            "PAYMENT_RECEIVED",
            "PAYMENT_OVERDUE",
            "PAYMENT_DELETED",
            "PAYMENT_REFUNDED",
            "PAYMENT_UPDATED",
            "PAYMENT_CHARGEBACK_REQUESTED",
            "PAYMENT_CHARGEBACK_DISPUTE",
            "PAYMENT_AWAITING_CHARGEBACK_REVERSAL"
    );

    private final CustomerRepository customerRepository;
    private final SubaccountRepository subaccountRepository;
    private final SubaccountApiKeyAuditRepository auditRepository;
    private final AsaasSubaccountClient asaasSubaccountClient;
    private final AsaasWebhookClient asaasWebhookClient;
    private final ApiKeyEncryptionService encryptionService;
    private final WebhookTokenGenerator webhookTokenGenerator;
    private final AsaasProperties asaasProperties;

    @Override
    @Transactional
    public SubaccountResponse create(CreateSubaccountRequest request) {
        log.info("Creating subaccount for customerId={}", request.getCustomerId());

        Customer customer = customerRepository.findById(request.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", request.getCustomerId()));

        if (customer.getAsaasCustomerId() == null) {
            throw new ResourceNotFoundException("Customer is not synced with Asaas. Sync customer first.");
        }

        if (subaccountRepository.existsByCustomerId(customer.getId())) {
            throw new DuplicateResourceException("Subaccount", "customerId", customer.getId());
        }

        // Phase 1: Insert PROVISIONING row
        Subaccount subaccount = SubaccountMapper.toEntity(request, customer);
        String webhookToken = webhookTokenGenerator.generate();
        subaccount.setWebhookToken(webhookToken);
        subaccount = subaccountRepository.save(subaccount);

        log.info("Subaccount provisioning started: subaccountId={}, customerId={}",
                subaccount.getId(), customer.getId());

        try {
            // Phase 2: Call Asaas API
            AsaasSubaccountRequest asaasRequest = SubaccountMapper.toAsaasRequest(customer, subaccount);
            AsaasSubaccountResponse asaasResponse = asaasSubaccountClient.createSubaccount(asaasRequest);

            // Store Asaas identifiers
            subaccount.setAsaasAccountId(asaasResponse.getId());
            subaccount.setAsaasWalletId(asaasResponse.getWalletId());

            // Encrypt and store the API key (returned once by Asaas)
            if (asaasResponse.getApiKey() != null) {
                byte[] encryptedKey = encryptionService.encrypt(asaasResponse.getApiKey());
                subaccount.setEncryptedApiKey(encryptedKey);

                auditRepository.save(SubaccountApiKeyAudit.builder()
                        .subaccount(subaccount)
                        .action(ApiKeyAuditAction.CREATED)
                        .performedBy("system:provisioning")
                        .details("API key encrypted and stored during subaccount creation")
                        .build());

                // Register webhook under the subaccount's context
                registerWebhook(asaasResponse.getApiKey(), webhookToken);
            } else {
                log.warn("Asaas did not return an API key for subaccount: asaasAccountId={}",
                        asaasResponse.getId());
            }

            // Transition to PENDING_EVALUATION (Asaas regulatory evaluation period)
            subaccount.transitionTo(SubaccountStatus.PENDING_EVALUATION,
                    "Subaccount created in Asaas — awaiting regulatory evaluation");
            subaccount = subaccountRepository.save(subaccount);

            log.info("Subaccount created successfully: subaccountId={}, asaasAccountId={}",
                    subaccount.getId(), asaasResponse.getId());

        } catch (Exception ex) {
            log.error("Failed to create subaccount in Asaas: subaccountId={}, error={}",
                    subaccount.getId(), ex.getMessage());

            subaccount.transitionTo(SubaccountStatus.FAILED,
                    "Asaas API call failed: " + truncate(ex.getMessage(), 400));
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
    public SubaccountResponse findByCustomerId(UUID customerId) {
        Subaccount subaccount = subaccountRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Subaccount", "customerId", customerId));
        return SubaccountMapper.toResponse(subaccount);
    }

    @Override
    @Transactional(readOnly = true)
    public void assertOutboundOperationsAllowed(UUID customerId) {
        Subaccount subaccount = subaccountRepository.findByCustomerId(customerId)
                .orElse(null);

        if (subaccount == null) {
            // No subaccount — operations use root key, always allowed
            return;
        }

        if (!subaccount.getStatus().allowsOutboundOperations()) {
            throw new SubaccountOperationBlockedException(
                    String.format("Subaccount %s is in status %s — outbound operations (charges, transfers) are blocked. %s",
                            subaccount.getId(),
                            subaccount.getStatus().name(),
                            subaccount.getStatus().getDescription()));
        }
    }

    private void registerWebhook(String apiKey, String webhookToken) {
        String webhookUrl = asaasProperties.getWebhookUrl();
        if (webhookUrl == null || webhookUrl.isBlank()) {
            log.warn("Webhook URL not configured — skipping webhook registration");
            return;
        }

        try {
            AsaasWebhookConfigRequest webhookRequest = AsaasWebhookConfigRequest.builder()
                    .name("Theron Wallet Webhook")
                    .url(webhookUrl)
                    .email("engineering@therongroup.com")
                    .enabled(true)
                    .interrupted(false)
                    .apiVersion("3")
                    .authToken(webhookToken)
                    .sendType("SEQUENTIALLY")
                    .events(PAYMENT_WEBHOOK_EVENTS)
                    .build();

            asaasWebhookClient.createWebhook(apiKey, webhookRequest);
            log.info("Webhook registered for subaccount with token prefix={}...", webhookToken.substring(0, 8));
        } catch (Exception ex) {
            log.error("Failed to register webhook for subaccount: {}", ex.getMessage());
            // Non-fatal: subaccount is still created, webhook can be retried
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
