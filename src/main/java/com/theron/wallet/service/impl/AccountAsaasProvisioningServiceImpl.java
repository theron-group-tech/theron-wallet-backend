package com.theron.wallet.service.impl;

import com.theron.wallet.config.AsaasProperties;
import com.theron.wallet.dto.asaas.AsaasSubaccountRequest;
import com.theron.wallet.dto.asaas.AsaasSubaccountResponse;
import com.theron.wallet.dto.asaas.AsaasWebhookConfigRequest;
import com.theron.wallet.dto.response.AsaasBindResponse;
import com.theron.wallet.entity.Account;
import com.theron.wallet.entity.Organization;
import com.theron.wallet.entity.Subaccount;
import com.theron.wallet.entity.SubaccountApiKeyAudit;
import com.theron.wallet.enums.ApiKeyAuditAction;
import com.theron.wallet.enums.AsaasBindStatus;
import com.theron.wallet.enums.AuditAction;
import com.theron.wallet.enums.DocumentType;
import com.theron.wallet.enums.SubaccountStatus;
import com.theron.wallet.exception.ApiErrorResponse;
import com.theron.wallet.exception.AsaasApiException;
import com.theron.wallet.exception.FieldValidationException;
import com.theron.wallet.exception.ResourceNotFoundException;
import com.theron.wallet.integration.AsaasErrorBodies;
import com.theron.wallet.integration.AsaasSubaccountClient;
import com.theron.wallet.mapper.SubaccountMapper;
import com.theron.wallet.repository.AccountRepository;
import com.theron.wallet.repository.SubaccountApiKeyAuditRepository;
import com.theron.wallet.repository.SubaccountRepository;
import com.theron.wallet.security.ApiKeyEncryptionService;
import com.theron.wallet.security.WebhookTokenGenerator;
import com.theron.wallet.service.AccountAsaasProvisioningService;
import com.theron.wallet.service.AuditLogService;
import com.theron.wallet.util.AsaasDocumentRules;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountAsaasProvisioningServiceImpl implements AccountAsaasProvisioningService {

    private static final AtomicBoolean WEBHOOK_SKIP_LOGGED = new AtomicBoolean(false);

    private static final List<String> WEBHOOK_EVENTS = List.of(
            "PAYMENT_CONFIRMED",
            "PAYMENT_RECEIVED",
            "PAYMENT_OVERDUE",
            "PAYMENT_DELETED",
            "PAYMENT_REFUNDED",
            "PAYMENT_UPDATED",
            "TRANSFER_CREATED",
            "TRANSFER_PENDING",
            "TRANSFER_DONE",
            "TRANSFER_FAILED",
            "TRANSFER_CANCELLED"
    );

    private final AccountRepository accountRepository;
    private final SubaccountRepository subaccountRepository;
    private final SubaccountApiKeyAuditRepository auditRepository;
    private final AsaasSubaccountClient asaasSubaccountClient;
    private final ApiKeyEncryptionService encryptionService;
    private final WebhookTokenGenerator webhookTokenGenerator;
    private final AsaasProperties asaasProperties;
    private final AuditLogService auditLogService;

    @Override
    @Transactional
    public AsaasBindResponse provisionByAccountId(UUID accountId, String documentOverride) {
        Account account = accountRepository.findByIdWithOrganization(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", "id", accountId));
        return provision(account, documentOverride);
    }

    @Override
    @Transactional(readOnly = true)
    public AsaasBindResponse currentBind(UUID accountId) {
        Account account = accountRepository.findByIdWithOrganization(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", "id", accountId));
        return subaccountRepository.findByAccount_Id(accountId)
                .map(sub -> toBind(account, sub))
                .orElseGet(() -> AsaasBindResponse.builder()
                        .accountId(account.getId())
                        .organizationId(account.getOrganization().getId())
                        .status(AsaasBindStatus.FAILED)
                        .message("Account is not linked to an Asaas subaccount")
                        .build());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasActiveBind(UUID accountId) {
        return subaccountRepository.findByAccount_Id(accountId)
                .map(this::isUsable)
                .orElse(false);
    }

    @Override
    @Transactional
    public AsaasBindResponse provision(Account account, String documentOverride) {
        Organization organization = account.getOrganization();
        Subaccount existing = subaccountRepository.findByAccount_Id(account.getId()).orElse(null);

        String documentSource = documentOverride;
        if (documentSource == null && existing != null
                && existing.getCpfCnpj() != null && !existing.getCpfCnpj().isBlank()) {
            documentSource = existing.getCpfCnpj();
        }
        if (documentSource == null) {
            documentSource = organization.getDocument();
        }

        String document = normalizeDocument(documentSource);
        validateCnpjForProvision(document, organization.getDocumentType());

        if (existing != null && isUsable(existing)) {
            return toBind(account, existing);
        }

        if (existing != null
                && existing.getAsaasAccountId() != null
                && existing.getStatus() == SubaccountStatus.PENDING_EVALUATION) {
            applyKyc(existing, account, organization, document);
            trySandboxApprove(existing);
            existing = subaccountRepository.save(existing);
            if (isUsable(existing)) {
                return toBind(account, existing);
            }
            return toBind(account, existing);
        }

        if (existing == null) {
            var occupied = subaccountRepository.findFirstByCpfCnpjAndStatusNot(document, SubaccountStatus.FAILED);
            if (occupied.isPresent()
                    && (occupied.get().getAccount() == null
                    || !occupied.get().getAccount().getId().equals(account.getId()))) {
                Subaccount failed = newFailedRow(account, organization, document,
                        "CPF/CNPJ already linked to another Asaas subaccount");
                failed = subaccountRepository.save(failed);
                return toBind(account, failed);
            }
            existing = newProvisioningRow(account, organization, document);
            existing = subaccountRepository.save(existing);
        } else {
            applyKyc(existing, account, organization, document);
            existing.setAsaasAccountId(null);
            existing.setAsaasWalletId(null);
            existing.setEncryptedApiKey(null);
            existing.transitionTo(SubaccountStatus.PROVISIONING, "Retrying Asaas subaccount provisioning");
            existing = subaccountRepository.save(existing);
        }

        try {
            List<AsaasWebhookConfigRequest> webhooks = buildWebhookConfig(existing.getWebhookToken());
            AsaasSubaccountRequest asaasRequest = SubaccountMapper.toAsaasRequest(existing, webhooks);
            AsaasSubaccountResponse asaasResponse = asaasSubaccountClient.createSubaccount(asaasRequest);

            existing.setAsaasAccountId(asaasResponse.getId());
            existing.setAsaasWalletId(asaasResponse.getWalletId());
            if (asaasResponse.getApiKey() != null) {
                existing.setEncryptedApiKey(encryptionService.encrypt(asaasResponse.getApiKey()));
                auditRepository.save(SubaccountApiKeyAudit.builder()
                        .subaccount(existing)
                        .action(ApiKeyAuditAction.CREATED)
                        .performedBy("system:account-provisioning")
                        .details("API key encrypted during Account Asaas provisioning")
                        .build());
            }
            existing.transitionTo(SubaccountStatus.PENDING_EVALUATION,
                    pendingEvaluationReason());
            existing = subaccountRepository.save(existing);
            trySandboxApprove(existing);
            existing = subaccountRepository.save(existing);
            auditLogService.record(
                    AuditAction.SUBACCOUNT_PROVISIONED,
                    organization.getId(),
                    account.getOwnerUser() != null ? account.getOwnerUser().getId() : null,
                    "Subaccount",
                    existing.getId(),
                    Map.of("accountId", account.getId().toString(), "status", existing.getStatus().name()));
        } catch (Exception ex) {
            String reason;
            if (ex instanceof AsaasApiException asaasEx) {
                log.error("Asaas provisioning failed: accountId={}, httpStatus={}, asaasBody={}",
                        account.getId(), asaasEx.getAsaasStatusCode(), asaasEx.getAsaasErrorBody());
                reason = truncate(AsaasErrorBodies.formatFailureReason(
                        asaasEx.getAsaasStatusCode(),
                        asaasEx.getAsaasErrorBody(),
                        asaasEx.getMessage()), 400);
            } else {
                log.error("Asaas provisioning failed: accountId={}, error={}", account.getId(), ex.getMessage());
                reason = truncate("Asaas API call failed: " + ex.getMessage(), 400);
            }
            existing.transitionTo(SubaccountStatus.FAILED, reason);
            existing = subaccountRepository.save(existing);
        }
        return toBind(account, existing);
    }

    private Subaccount newProvisioningRow(
            Account account, Organization organization, String document) {
        Subaccount subaccount = Subaccount.builder()
                .account(account)
                .webhookToken(webhookTokenGenerator.generate())
                .status(SubaccountStatus.PROVISIONING)
                .build();
        applyKyc(subaccount, account, organization, document);
        return subaccount;
    }

    private Subaccount newFailedRow(
            Account account, Organization organization, String document, String reason) {
        Subaccount subaccount = Subaccount.builder()
                .account(account)
                .webhookToken(webhookTokenGenerator.generate())
                .status(SubaccountStatus.FAILED)
                .statusReason(reason)
                .build();
        applyKyc(subaccount, account, organization, document);
        return subaccount;
    }

    private void applyKyc(
            Subaccount subaccount,
            Account account,
            Organization organization,
            String document) {
        AsaasProperties.SubaccountDefaults defaults = asaasProperties.getSubaccountDefaults();
        String ownerEmail = account.getOwnerUser() != null ? account.getOwnerUser().getEmail() : null;
        String email = ownerEmail != null && !ownerEmail.isBlank()
                ? ownerEmail.trim()
                : account.getId() + "@asaas.theron.internal";
        String ownerPhone = account.getOwnerUser() != null ? account.getOwnerUser().getPhone() : null;
        String mobile = ownerPhone != null && !ownerPhone.isBlank()
                ? ownerPhone.replaceAll("\\D", "")
                : defaults.getMobile();
        if (mobile == null || mobile.isBlank()) {
            mobile = defaults.getMobile();
        }

        subaccount.setName(account.getName());
        subaccount.setEmail(email);
        subaccount.setLoginEmail(email);
        subaccount.setCpfCnpj(document);
        subaccount.setMobilePhone(mobile);
        subaccount.setPhone(mobile);
        subaccount.setAddress(defaults.getAddress());
        subaccount.setAddressNumber(defaults.getAddressNumber());
        subaccount.setProvince(defaults.getProvince());
        subaccount.setPostalCode(defaults.getPostalCode());
        subaccount.setIncomeValue(new BigDecimal(defaults.getIncomeValue()));
        subaccount.setBirthDate(null);
        subaccount.setCompanyType(defaults.getCompanyType());
    }

    private boolean isUsable(Subaccount subaccount) {
        return subaccount.getEncryptedApiKey() != null
                && subaccount.getAsaasAccountId() != null
                && subaccount.getStatus() == SubaccountStatus.ACTIVE;
    }

    private void trySandboxApprove(Subaccount subaccount) {
        if (!asaasProperties.isAutoApproveSubaccounts()) {
            if (subaccount.getStatus() == SubaccountStatus.PENDING_EVALUATION
                    && (subaccount.getStatusReason() == null || subaccount.getStatusReason().isBlank())) {
                subaccount.setStatusReason(awaitingActivationMessage());
            }
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

    private static String pendingEvaluationReason() {
        return "Subaccount created in Asaas — awaiting activation/approval";
    }

    private static String awaitingActivationMessage() {
        return "Subconta criada; aguardando ativação/aprovação Asaas (Sandbox: approve automático após reinício do BE).";
    }

    private AsaasBindResponse toBind(Account account, Subaccount subaccount) {
        return AsaasBindResponse.builder()
                .accountId(account.getId())
                .organizationId(account.getOrganization().getId())
                .asaasAccountId(subaccount.getAsaasAccountId())
                .asaasWalletId(subaccount.getAsaasWalletId())
                .status(toPublicStatus(subaccount))
                .message(resolveBindMessage(subaccount))
                .build();
    }

    private String resolveBindMessage(Subaccount subaccount) {
        if (subaccount.getStatus() == SubaccountStatus.FAILED) {
            return subaccount.getStatusReason();
        }
        if (subaccount.getStatus() == SubaccountStatus.PENDING_EVALUATION) {
            if (subaccount.getStatusReason() != null && !subaccount.getStatusReason().isBlank()) {
                return subaccount.getStatusReason();
            }
            return awaitingActivationMessage();
        }
        return null;
    }

    private AsaasBindStatus toPublicStatus(Subaccount subaccount) {
        if (isUsable(subaccount)) {
            return AsaasBindStatus.ACTIVE;
        }
        if (subaccount.getStatus() == SubaccountStatus.FAILED) {
            return AsaasBindStatus.FAILED;
        }
        return AsaasBindStatus.PENDING;
    }

    private List<AsaasWebhookConfigRequest> buildWebhookConfig(String webhookToken) {
        String webhookUrl = asaasProperties.getWebhookUrl();
        if (webhookUrl == null || webhookUrl.isBlank()) {
            if (WEBHOOK_SKIP_LOGGED.compareAndSet(false, true)) {
                log.info("ASAAS_WEBHOOK_URL not set — subaccounts are created without inline webhooks");
            }
            return null;
        }
        return List.of(AsaasWebhookConfigRequest.builder()
                .name("Theron Wallet")
                .url(webhookUrl)
                .email("webhooks@theron.internal")
                .enabled(true)
                .interrupted(false)
                .apiVersion("3")
                .authToken(webhookToken)
                .sendType("SEQUENTIALLY")
                .events(WEBHOOK_EVENTS)
                .build());
    }

    private static String normalizeDocument(String document) {
        return AsaasDocumentRules.normalize(document);
    }

    private static void validateCnpjForProvision(String document, DocumentType organizationDocumentType) {
        if (organizationDocumentType == DocumentType.CPF) {
            throw new FieldValidationException(
                    AsaasDocumentRules.CNPJ_REQUIRED_MESSAGE,
                    List.of(ApiErrorResponse.FieldError.builder()
                            .field("document")
                            .message("Organization must use CNPJ for Asaas subaccount provisioning")
                            .build()));
        }
        if (document == null || document.isBlank()) {
            throw new FieldValidationException(
                    "CNPJ is required before Asaas provisioning",
                    List.of(ApiErrorResponse.FieldError.builder()
                            .field("document")
                            .message("document is required")
                            .build()));
        }
        if (document.length() == 11) {
            throw new FieldValidationException(
                    AsaasDocumentRules.CNPJ_REQUIRED_MESSAGE,
                    List.of(ApiErrorResponse.FieldError.builder()
                            .field("document")
                            .message(AsaasDocumentRules.CNPJ_REQUIRED_MESSAGE)
                            .rejectedValue(document)
                            .build()));
        }
        if (document.length() != 14) {
            throw new FieldValidationException(
                    "Invalid CNPJ",
                    List.of(ApiErrorResponse.FieldError.builder()
                            .field("document")
                            .message("CNPJ must contain exactly 14 digits")
                            .rejectedValue(document)
                            .build()));
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "unknown error";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
