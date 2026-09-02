package com.theron.wallet.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theron.wallet.config.AsaasProperties;
import com.theron.wallet.domain.onboarding.AsaasOnboardingPayload;
import com.theron.wallet.dto.asaas.AsaasAccountStatusResponse;
import com.theron.wallet.dto.asaas.AsaasSubaccountRequest;
import com.theron.wallet.dto.asaas.AsaasSubaccountResponse;
import com.theron.wallet.dto.asaas.AsaasWebhookConfigRequest;
import com.theron.wallet.dto.request.onboarding.OnboardingAccountTypeRequest;
import com.theron.wallet.dto.request.onboarding.OnboardingAddressRequest;
import com.theron.wallet.dto.request.onboarding.OnboardingBusinessRequest;
import com.theron.wallet.dto.request.onboarding.OnboardingFinancialRequest;
import com.theron.wallet.dto.request.onboarding.OnboardingPersonalRequest;
import com.theron.wallet.dto.response.AccountResponse;
import com.theron.wallet.dto.response.AsaasOnboardingResponse;
import com.theron.wallet.dto.response.AsaasSubaccountStatusResponse;
import com.theron.wallet.entity.Account;
import com.theron.wallet.entity.AsaasOnboarding;
import com.theron.wallet.entity.Subaccount;
import com.theron.wallet.entity.SubaccountApiKeyAudit;
import com.theron.wallet.enums.ApiKeyAuditAction;
import com.theron.wallet.enums.AsaasOnboardingStatus;
import com.theron.wallet.enums.AsaasOnboardingStep;
import com.theron.wallet.enums.AsaasPersonType;
import com.theron.wallet.enums.AuditAction;
import com.theron.wallet.enums.SubaccountStatus;
import com.theron.wallet.exception.AsaasApiException;
import com.theron.wallet.exception.ForbiddenException;
import com.theron.wallet.exception.InvalidRequestException;
import com.theron.wallet.exception.ResourceNotFoundException;
import com.theron.wallet.integration.AsaasAccountStatusClient;
import com.theron.wallet.integration.AsaasErrorBodies;
import com.theron.wallet.integration.AsaasSubaccountClient;
import com.theron.wallet.mapper.SubaccountMapper;
import com.theron.wallet.repository.AccountRepository;
import com.theron.wallet.repository.AsaasOnboardingRepository;
import com.theron.wallet.repository.SubaccountApiKeyAuditRepository;
import com.theron.wallet.repository.SubaccountRepository;
import com.theron.wallet.security.ApiKeyEncryptionService;
import com.theron.wallet.security.OrganizationContextResolver;
import com.theron.wallet.security.WebhookTokenGenerator;
import com.theron.wallet.service.AsaasOnboardingService;
import com.theron.wallet.service.AuditLogService;
import com.theron.wallet.util.AsaasDocumentRules;
import com.theron.wallet.util.CepValidator;
import com.theron.wallet.util.CpfCnpjValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class AsaasOnboardingServiceImpl implements AsaasOnboardingService {

    private static final AtomicBoolean WEBHOOK_SKIP_LOGGED = new AtomicBoolean(false);

    private static final List<String> ACCOUNT_STATUS_WEBHOOK_EVENTS = List.of(
            "ACCOUNT_STATUS_BANK_ACCOUNT_INFO_APPROVED",
            "ACCOUNT_STATUS_BANK_ACCOUNT_INFO_AWAITING_APPROVAL",
            "ACCOUNT_STATUS_BANK_ACCOUNT_INFO_PENDING",
            "ACCOUNT_STATUS_BANK_ACCOUNT_INFO_REJECTED",
            "ACCOUNT_STATUS_COMMERCIAL_INFO_APPROVED",
            "ACCOUNT_STATUS_COMMERCIAL_INFO_AWAITING_APPROVAL",
            "ACCOUNT_STATUS_COMMERCIAL_INFO_EXPIRED",
            "ACCOUNT_STATUS_COMMERCIAL_INFO_EXPIRING_SOON",
            "ACCOUNT_STATUS_COMMERCIAL_INFO_PENDING",
            "ACCOUNT_STATUS_COMMERCIAL_INFO_REJECTED",
            "ACCOUNT_STATUS_DOCUMENT_APPROVED",
            "ACCOUNT_STATUS_DOCUMENT_AWAITING_APPROVAL",
            "ACCOUNT_STATUS_DOCUMENT_PENDING",
            "ACCOUNT_STATUS_DOCUMENT_REJECTED",
            "ACCOUNT_STATUS_GENERAL_APPROVAL_APPROVED",
            "ACCOUNT_STATUS_GENERAL_APPROVAL_AWAITING_APPROVAL",
            "ACCOUNT_STATUS_GENERAL_APPROVAL_PENDING",
            "ACCOUNT_STATUS_GENERAL_APPROVAL_REJECTED");

    private static final List<String> WEBHOOK_EVENTS = Stream.concat(
            Stream.of(
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
                    "TRANSFER_CANCELLED"),
            ACCOUNT_STATUS_WEBHOOK_EVENTS.stream()).toList();

    private final OrganizationContextResolver organizationContextResolver;
    private final AccountRepository accountRepository;
    private final AsaasOnboardingRepository onboardingRepository;
    private final SubaccountRepository subaccountRepository;
    private final SubaccountApiKeyAuditRepository auditRepository;
    private final AsaasSubaccountClient asaasSubaccountClient;
    private final AsaasAccountStatusClient asaasAccountStatusClient;
    private final ApiKeyEncryptionService encryptionService;
    private final WebhookTokenGenerator webhookTokenGenerator;
    private final AsaasProperties asaasProperties;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public AsaasOnboardingResponse startOrResume(UUID actorUserId) {
        Account account = requireOwnAccount(actorUserId);
        if (subaccountRepository.findByAccount_Id(account.getId()).filter(this::hasSubmittedSubaccount).isPresent()) {
            return toResponse(onboardingRepository.findByAccountId(account.getId()).orElse(null), account);
        }
        AsaasOnboarding onboarding = onboardingRepository.findByAccountId(account.getId())
                .orElseGet(() -> createOnboarding(actorUserId, account));
        if (onboarding.getStatus() == AsaasOnboardingStatus.NOT_STARTED) {
            onboarding.setStatus(AsaasOnboardingStatus.IN_PROGRESS);
            onboarding = onboardingRepository.save(onboarding);
        }
        return toResponse(onboarding, account);
    }

    @Override
    @Transactional(readOnly = true)
    public AsaasOnboardingResponse getCurrent(UUID actorUserId) {
        Account account = requireOwnAccount(actorUserId);
        return toResponse(onboardingRepository.findByAccountId(account.getId()).orElse(null), account);
    }

    @Override
    @Transactional
    public AsaasOnboardingResponse saveAccountType(UUID actorUserId, OnboardingAccountTypeRequest request) {
        AsaasOnboarding onboarding = requireEditableOnboarding(actorUserId);
        onboarding.setPersonType(request.getPersonType());
        onboarding.setCurrentStep(request.getPersonType() == AsaasPersonType.COMPANY
                ? AsaasOnboardingStep.BUSINESS_DATA
                : AsaasOnboardingStep.PERSONAL_DATA);
        onboarding.setStatus(AsaasOnboardingStatus.IN_PROGRESS);
        return toResponse(onboardingRepository.save(onboarding), onboarding.getAccount());
    }

    @Override
    @Transactional
    public AsaasOnboardingResponse savePersonal(UUID actorUserId, OnboardingPersonalRequest request) {
        AsaasOnboarding onboarding = requireEditableOnboarding(actorUserId);
        if (onboarding.getPersonType() != AsaasPersonType.INDIVIDUAL) {
            throw new InvalidRequestException("Personal data is only valid for INDIVIDUAL accounts");
        }
        String cpf = AsaasDocumentRules.normalize(request.getCpf());
        if (!CpfCnpjValidator.isValidCpf(cpf)) {
            throw new InvalidRequestException("Invalid CPF");
        }
        AsaasOnboardingPayload payload = readPayload(onboarding);
        payload.setName(request.getName().trim());
        payload.setCpfCnpj(cpf);
        payload.setBirthDate(request.getBirthDate());
        payload.setEmail(request.getEmail().trim());
        payload.setMobilePhone(normalizePhone(request.getMobilePhone()));
        payload.setCompanyType(null);
        writePayload(onboarding, payload);
        onboarding.setCurrentStep(AsaasOnboardingStep.ADDRESS);
        return toResponse(onboardingRepository.save(onboarding), onboarding.getAccount());
    }

    @Override
    @Transactional
    public AsaasOnboardingResponse saveBusiness(UUID actorUserId, OnboardingBusinessRequest request) {
        AsaasOnboarding onboarding = requireEditableOnboarding(actorUserId);
        if (onboarding.getPersonType() != AsaasPersonType.COMPANY) {
            throw new InvalidRequestException("Business data is only valid for COMPANY accounts");
        }
        String cnpj = AsaasDocumentRules.normalize(request.getCnpj());
        if (!CpfCnpjValidator.isValidCnpj(cnpj)) {
            throw new InvalidRequestException("Invalid CNPJ");
        }
        AsaasOnboardingPayload payload = readPayload(onboarding);
        payload.setName(request.getLegalName().trim());
        payload.setTradeName(request.getTradeName());
        payload.setCpfCnpj(cnpj);
        payload.setBirthDate(null);
        payload.setEmail(request.getEmail().trim());
        payload.setMobilePhone(normalizePhone(request.getMobilePhone()));
        payload.setCompanyType(request.getCompanyType());
        writePayload(onboarding, payload);
        onboarding.setCurrentStep(AsaasOnboardingStep.ADDRESS);
        return toResponse(onboardingRepository.save(onboarding), onboarding.getAccount());
    }

    @Override
    @Transactional
    public AsaasOnboardingResponse saveAddress(UUID actorUserId, OnboardingAddressRequest request) {
        AsaasOnboarding onboarding = requireEditableOnboarding(actorUserId);
        CepValidator.requireValid(request.getPostalCode());
        AsaasOnboardingPayload payload = readPayload(onboarding);
        payload.setAddress(request.getAddress().trim());
        payload.setAddressNumber(request.getAddressNumber().trim());
        payload.setComplement(request.getComplement());
        payload.setProvince(request.getProvince().trim());
        payload.setPostalCode(CepValidator.normalize(request.getPostalCode()));
        payload.setCity(request.getCity());
        writePayload(onboarding, payload);
        onboarding.setCurrentStep(AsaasOnboardingStep.FINANCIAL);
        return toResponse(onboardingRepository.save(onboarding), onboarding.getAccount());
    }

    @Override
    @Transactional
    public AsaasOnboardingResponse saveFinancial(UUID actorUserId, OnboardingFinancialRequest request) {
        AsaasOnboarding onboarding = requireEditableOnboarding(actorUserId);
        AsaasOnboardingPayload payload = readPayload(onboarding);
        payload.setIncomeValue(request.getIncomeValue());
        writePayload(onboarding, payload);
        onboarding.setCurrentStep(AsaasOnboardingStep.REVIEW);
        return toResponse(onboardingRepository.save(onboarding), onboarding.getAccount());
    }

    @Override
    @Transactional
    public AsaasOnboardingResponse submit(UUID actorUserId, String idempotencyKey) {
        Account account = requireOwnAccount(actorUserId);
        AsaasOnboarding onboarding = onboardingRepository.findByAccountIdForUpdate(account.getId())
                .orElseThrow(() -> new InvalidRequestException("Onboarding not started"));

        Subaccount existing = subaccountRepository.findByAccount_Id(account.getId()).orElse(null);
        if (existing != null && existing.getAsaasAccountId() != null) {
            return toResponse(onboarding, account);
        }

        if (onboarding.getStatus() == AsaasOnboardingStatus.SUBACCOUNT_CREATED
                || onboarding.getStatus() == AsaasOnboardingStatus.PENDING_DOCUMENTS
                || onboarding.getStatus() == AsaasOnboardingStatus.UNDER_ANALYSIS
                || onboarding.getStatus() == AsaasOnboardingStatus.APPROVED) {
            return toResponse(onboarding, account);
        }

        if (idempotencyKey != null && !idempotencyKey.isBlank()
                && idempotencyKey.equals(onboarding.getIdempotencyKey())
                && onboarding.getAsaasAccountId() != null) {
            return toResponse(onboarding, account);
        }

        validateReadyForSubmit(onboarding);
        AsaasOnboardingPayload payload = readPayload(onboarding);

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            onboarding.setIdempotencyKey(idempotencyKey);
        }
        onboarding.setSubmitAttempts(onboarding.getSubmitAttempts() + 1);
        onboarding.setLastErrorCode(null);
        onboarding.setLastErrorMessage(null);

        Subaccount subaccount = existing != null ? existing : Subaccount.builder()
                .account(account)
                .status(SubaccountStatus.PROVISIONING)
                .webhookToken(webhookTokenGenerator.generate())
                .legacyAutoProvisioned(false)
                .onboarding(onboarding)
                .personType(onboarding.getPersonType())
                .build();

        applyPayloadToSubaccount(subaccount, payload);
        subaccount = subaccountRepository.save(subaccount);

        try {
            List<AsaasWebhookConfigRequest> webhooks = buildWebhookConfig(subaccount.getWebhookToken());
            AsaasSubaccountRequest asaasRequest = SubaccountMapper.toAsaasRequest(subaccount, webhooks);
            if (onboarding.getPersonType() == AsaasPersonType.INDIVIDUAL) {
                asaasRequest.setBirthDate(payload.getBirthDate());
                asaasRequest.setCompanyType(null);
            } else {
                asaasRequest.setBirthDate(null);
            }

            AsaasSubaccountResponse asaasResponse = asaasSubaccountClient.createSubaccount(asaasRequest);
            subaccount.setAsaasAccountId(asaasResponse.getId());
            subaccount.setAsaasWalletId(asaasResponse.getWalletId());
            if (asaasResponse.getApiKey() != null) {
                subaccount.setEncryptedApiKey(encryptionService.encrypt(asaasResponse.getApiKey()));
                auditRepository.save(SubaccountApiKeyAudit.builder()
                        .subaccount(subaccount)
                        .action(ApiKeyAuditAction.CREATED)
                        .performedBy("system:onboarding-submit")
                        .details("API key encrypted during self-service onboarding")
                        .build());
            }
            subaccount.transitionTo(SubaccountStatus.PENDING_EVALUATION, "Subaccount created via onboarding");
            subaccount = subaccountRepository.save(subaccount);

            onboarding.setAsaasAccountId(asaasResponse.getId());
            onboarding.setCurrentStep(AsaasOnboardingStep.SUBMITTED);
            onboarding.setStatus(AsaasOnboardingStatus.SUBACCOUNT_CREATED);
            syncAfterCreate(subaccount, onboarding);
            subaccount = subaccountRepository.save(subaccount);
            onboarding = onboardingRepository.save(onboarding);

            auditLogService.record(
                    AuditAction.SUBACCOUNT_PROVISIONED,
                    account.getOrganization().getId(),
                    actorUserId,
                    "AsaasOnboarding",
                    onboarding.getId(),
                    java.util.Map.of(
                            "accountId", account.getId().toString(),
                            "personType", onboarding.getPersonType().name(),
                            "asaasAccountId", asaasResponse.getId()));
        } catch (RuntimeException ex) {
            String code = ex instanceof AsaasApiException asaasEx
                    ? "ASAAS_" + asaasEx.getAsaasStatusCode()
                    : "SUBMIT_FAILED";
            onboarding.setLastErrorCode(code);
            onboarding.setLastErrorMessage(truncate(ex.getMessage(), 480));
            onboardingRepository.save(onboarding);
            if (subaccount.getId() != null && subaccount.getAsaasAccountId() == null) {
                subaccount.transitionTo(SubaccountStatus.FAILED, truncate(ex.getMessage(), 480));
                subaccountRepository.save(subaccount);
            }
            throw ex instanceof AsaasApiException
                    ? ex
                    : new InvalidRequestException("Failed to create Asaas subaccount: " + ex.getMessage());
        }

        return toResponse(onboarding, account);
    }

    @Override
    @Transactional
    public AsaasSubaccountStatusResponse subaccountStatus(UUID actorUserId) {
        Account account = requireOwnAccount(actorUserId);
        refreshOperationalStatusFromAsaas(account.getId());
        Subaccount subaccount = subaccountRepository.findByAccount_Id(account.getId()).orElse(null);
        AsaasOnboarding onboarding = onboardingRepository.findByAccountId(account.getId()).orElse(null);
        boolean enabled = isFinancialResourcesEnabled(account.getId());
        return AsaasSubaccountStatusResponse.builder()
                .accountId(account.getId())
                .hasSubaccount(subaccount != null && subaccount.getAsaasAccountId() != null)
                .subaccountStatus(subaccount != null ? subaccount.getStatus() : null)
                .onboardingStatus(onboarding != null ? onboarding.getStatus() : AsaasOnboardingStatus.NOT_STARTED)
                .onboardingUrl(onboarding != null ? onboarding.getOnboardingUrl() : null)
                .financialResourcesEnabled(enabled)
                .message(resolveStatusMessage(subaccount, onboarding, enabled))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isFinancialResourcesEnabled(UUID accountId) {
        Subaccount subaccount = subaccountRepository.findByAccount_Id(accountId).orElse(null);
        if (subaccount == null || subaccount.getEncryptedApiKey() == null) {
            return false;
        }
        if (subaccount.isLegacyAutoProvisioned()) {
            return subaccount.getStatus() == SubaccountStatus.ACTIVE;
        }
        AsaasOnboarding onboarding = subaccount.getOnboarding();
        if (onboarding == null) {
            onboarding = onboardingRepository.findByAccountId(accountId).orElse(null);
        }
        return subaccount.getStatus() == SubaccountStatus.ACTIVE
                && onboarding != null
                && onboarding.getStatus() == AsaasOnboardingStatus.APPROVED;
    }

    @Override
    @Transactional(readOnly = true)
    public void enrichAccountResponse(AccountResponse response, UUID accountId) {
        if (response == null || accountId == null) {
            return;
        }
        response.setFinancialResourcesEnabled(isFinancialResourcesEnabled(accountId));
        AsaasOnboarding onboarding = onboardingRepository.findByAccountId(accountId).orElse(null);
        if (onboarding != null) {
            response.setOnboardingStatus(onboarding.getStatus());
            if (response.getOnboardingUrl() == null) {
                response.setOnboardingUrl(onboarding.getOnboardingUrl());
            }
        }
    }

    @Override
    @Transactional
    public void applyAccountStatusWebhook(String asaasAccountId, String eventName) {
        if (asaasAccountId == null || asaasAccountId.isBlank()) {
            return;
        }
        onboardingRepository.findByAsaasAccountId(asaasAccountId).ifPresent(onboarding -> {
            Subaccount subaccount = subaccountRepository.findByAccount_Id(onboarding.getAccount().getId())
                    .orElse(null);
            if (subaccount == null) {
                return;
            }
            mapAccountEvent(onboarding, subaccount, eventName);
            onboardingRepository.save(onboarding);
            subaccountRepository.save(subaccount);
        });
    }

    private void mapAccountEvent(AsaasOnboarding onboarding, Subaccount subaccount, String eventName) {
        if (eventName == null || !eventName.startsWith("ACCOUNT_STATUS_")) {
            return;
        }
        if ("ACCOUNT_STATUS_GENERAL_APPROVAL_APPROVED".equals(eventName)) {
            subaccount.transitionTo(SubaccountStatus.ACTIVE, "Approved via Asaas webhook");
            onboarding.setStatus(AsaasOnboardingStatus.APPROVED);
            onboarding.setCurrentStep(AsaasOnboardingStep.COMPLETED);
            return;
        }
        if (eventName.endsWith("_REJECTED")) {
            onboarding.setStatus(AsaasOnboardingStatus.REJECTED);
            subaccount.transitionTo(SubaccountStatus.FAILED, "Rejected via Asaas webhook");
            return;
        }
        if (eventName.endsWith("_AWAITING_APPROVAL")) {
            onboarding.setStatus(AsaasOnboardingStatus.UNDER_ANALYSIS);
            return;
        }
        if (eventName.endsWith("_PENDING")
                || eventName.endsWith("_EXPIRED")
                || eventName.endsWith("_EXPIRING_SOON")) {
            onboarding.setStatus(AsaasOnboardingStatus.PENDING_DOCUMENTS);
            return;
        }
        if (eventName.endsWith("_APPROVED")) {
            onboarding.setStatus(AsaasOnboardingStatus.UNDER_ANALYSIS);
        }
    }

    private void syncAfterCreate(Subaccount subaccount, AsaasOnboarding onboarding) {
        trySandboxApprove(subaccount);
        applyOperationalStatusFromAsaas(subaccount, onboarding);
    }

    private void refreshOperationalStatusFromAsaas(UUID accountId) {
        Subaccount subaccount = subaccountRepository.findByAccount_Id(accountId).orElse(null);
        if (subaccount == null
                || subaccount.getEncryptedApiKey() == null
                || subaccount.isLegacyAutoProvisioned()) {
            return;
        }
        AsaasOnboarding onboarding = onboardingRepository.findByAccountId(accountId).orElse(null);
        if (onboarding == null) {
            return;
        }
        if (subaccount.getStatus() == SubaccountStatus.ACTIVE
                && onboarding.getStatus() == AsaasOnboardingStatus.APPROVED) {
            return;
        }
        applyOperationalStatusFromAsaas(subaccount, onboarding);
        subaccountRepository.save(subaccount);
        onboardingRepository.save(onboarding);
    }

    private void applyOperationalStatusFromAsaas(Subaccount subaccount, AsaasOnboarding onboarding) {
        if (subaccount.getEncryptedApiKey() == null) {
            return;
        }
        try {
            String apiKey = encryptionService.decrypt(subaccount.getEncryptedApiKey());
            AsaasAccountStatusResponse status = asaasAccountStatusClient.getStatus(apiKey);
            String general = status != null ? status.getGeneral() : null;
            String commercial = status != null ? status.getCommercialInfo() : null;
            boolean approved = isApproved(general) || isApproved(commercial);
            if (!approved) {
                String onboardingUrl = asaasAccountStatusClient.firstOnboardingUrl(apiKey);
                onboarding.setOnboardingUrl(onboardingUrl);
                if (onboarding.getStatus() != AsaasOnboardingStatus.REJECTED) {
                    onboarding.setStatus(AsaasOnboardingStatus.PENDING_DOCUMENTS);
                    onboarding.setCurrentStep(AsaasOnboardingStep.DOCUMENTATION);
                }
            } else {
                subaccount.transitionTo(SubaccountStatus.ACTIVE, "Synced from Asaas account status");
                onboarding.setStatus(AsaasOnboardingStatus.APPROVED);
                onboarding.setCurrentStep(AsaasOnboardingStep.COMPLETED);
            }
        } catch (Exception ex) {
            log.warn("Asaas operational status sync failed: accountId={}, error={}",
                    subaccount.getAccount().getId(), ex.getMessage());
            if (onboarding.getStatus() == AsaasOnboardingStatus.SUBACCOUNT_CREATED) {
                onboarding.setStatus(AsaasOnboardingStatus.UNDER_ANALYSIS);
            }
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
            log.warn("Sandbox approve after onboarding failed: subaccountId={}, error={}",
                    subaccount.getId(), ex.getMessage());
        }
    }

    private AsaasOnboarding createOnboarding(UUID actorUserId, Account account) {
        return onboardingRepository.save(AsaasOnboarding.builder()
                .user(account.getOwnerUser())
                .account(account)
                .status(AsaasOnboardingStatus.NOT_STARTED)
                .currentStep(AsaasOnboardingStep.ACCOUNT_TYPE)
                .build());
    }

    private AsaasOnboarding requireEditableOnboarding(UUID actorUserId) {
        Account account = requireOwnAccount(actorUserId);
        AsaasOnboarding onboarding = onboardingRepository.findByAccountId(account.getId())
                .orElseGet(() -> createOnboarding(actorUserId, account));
        if (onboarding.getStatus() == AsaasOnboardingStatus.APPROVED
                || onboarding.getStatus() == AsaasOnboardingStatus.BLOCKED) {
            throw new InvalidRequestException("Onboarding cannot be edited in status " + onboarding.getStatus());
        }
        if (subaccountRepository.findByAccount_Id(account.getId()).filter(this::hasSubmittedSubaccount).isPresent()) {
            throw new InvalidRequestException("Subaccount already created for this account");
        }
        if (onboarding.getStatus() == AsaasOnboardingStatus.NOT_STARTED) {
            onboarding.setStatus(AsaasOnboardingStatus.IN_PROGRESS);
        }
        return onboarding;
    }

    private Account requireOwnAccount(UUID actorUserId) {
        UUID organizationId = organizationContextResolver.requireSingleOrganizationId(actorUserId);
        return accountRepository.findByOrganization_IdAndOwnerUser_Id(organizationId, actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", "ownerUserId", actorUserId));
    }

    private void validateReadyForSubmit(AsaasOnboarding onboarding) {
        if (onboarding.getPersonType() == null) {
            throw new InvalidRequestException("Account type is required");
        }
        if (onboarding.getCurrentStep() != AsaasOnboardingStep.REVIEW
                && onboarding.getCurrentStep() != AsaasOnboardingStep.FINANCIAL) {
            throw new InvalidRequestException("Complete all onboarding steps before submit");
        }
        AsaasOnboardingPayload payload = readPayload(onboarding);
        if (payload.getName() == null || payload.getCpfCnpj() == null || payload.getEmail() == null
                || payload.getMobilePhone() == null || payload.getAddress() == null
                || payload.getAddressNumber() == null || payload.getProvince() == null
                || payload.getPostalCode() == null || payload.getIncomeValue() == null) {
            throw new InvalidRequestException("Onboarding data is incomplete");
        }
        if (onboarding.getPersonType() == AsaasPersonType.INDIVIDUAL
                && (payload.getBirthDate() == null || payload.getBirthDate().isBlank())) {
            throw new InvalidRequestException("birthDate is required for CPF accounts");
        }
        if (onboarding.getPersonType() == AsaasPersonType.COMPANY
                && (payload.getCompanyType() == null || payload.getCompanyType().isBlank())) {
            throw new InvalidRequestException("companyType is required for CNPJ accounts");
        }
        CepValidator.requireValid(payload.getPostalCode());
    }

    private void applyPayloadToSubaccount(Subaccount subaccount, AsaasOnboardingPayload payload) {
        subaccount.setName(payload.getName());
        subaccount.setEmail(payload.getEmail());
        subaccount.setLoginEmail(payload.getEmail());
        subaccount.setCpfCnpj(payload.getCpfCnpj());
        subaccount.setMobilePhone(payload.getMobilePhone());
        subaccount.setPhone(payload.getMobilePhone());
        subaccount.setAddress(payload.getAddress());
        subaccount.setAddressNumber(payload.getAddressNumber());
        subaccount.setComplement(payload.getComplement());
        subaccount.setProvince(payload.getProvince());
        subaccount.setPostalCode(payload.getPostalCode());
        subaccount.setIncomeValue(payload.getIncomeValue());
        subaccount.setBirthDate(payload.getBirthDate());
        subaccount.setCompanyType(
                subaccount.getPersonType() == AsaasPersonType.COMPANY ? payload.getCompanyType() : null);
    }

    private AsaasOnboardingPayload readPayload(AsaasOnboarding onboarding) {
        if (onboarding.getPayloadJson() == null || onboarding.getPayloadJson().isBlank()) {
            return new AsaasOnboardingPayload();
        }
        try {
            return objectMapper.readValue(onboarding.getPayloadJson(), AsaasOnboardingPayload.class);
        } catch (JsonProcessingException ex) {
            throw new InvalidRequestException("Invalid onboarding payload state");
        }
    }

    private void writePayload(AsaasOnboarding onboarding, AsaasOnboardingPayload payload) {
        try {
            onboarding.setPayloadJson(objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException ex) {
            throw new InvalidRequestException("Failed to persist onboarding payload");
        }
    }

    private AsaasOnboardingResponse toResponse(AsaasOnboarding onboarding, Account account) {
        if (onboarding == null) {
            boolean enabled = isFinancialResourcesEnabled(account.getId());
            Subaccount subaccount = subaccountRepository.findByAccount_Id(account.getId()).orElse(null);
            AsaasOnboardingStatus status = subaccount != null && subaccount.isLegacyAutoProvisioned()
                    ? (enabled ? AsaasOnboardingStatus.APPROVED : AsaasOnboardingStatus.PENDING_DOCUMENTS)
                    : AsaasOnboardingStatus.NOT_STARTED;
            return AsaasOnboardingResponse.builder()
                    .accountId(account.getId())
                    .status(status)
                    .currentStep(AsaasOnboardingStep.ACCOUNT_TYPE)
                    .financialResourcesEnabled(enabled)
                    .build();
        }
        AsaasOnboardingPayload review = onboarding.getCurrentStep() == AsaasOnboardingStep.REVIEW
                || onboarding.getCurrentStep() == AsaasOnboardingStep.FINANCIAL
                ? readPayload(onboarding)
                : null;
        return AsaasOnboardingResponse.builder()
                .id(onboarding.getId())
                .accountId(account.getId())
                .personType(onboarding.getPersonType())
                .currentStep(onboarding.getCurrentStep())
                .status(onboarding.getStatus())
                .review(review)
                .onboardingUrl(onboarding.getOnboardingUrl())
                .asaasAccountId(onboarding.getAsaasAccountId())
                .lastErrorCode(onboarding.getLastErrorCode())
                .lastErrorMessage(onboarding.getLastErrorMessage())
                .financialResourcesEnabled(isFinancialResourcesEnabled(account.getId()))
                .build();
    }

    private boolean hasSubmittedSubaccount(Subaccount subaccount) {
        return subaccount.getAsaasAccountId() != null && !subaccount.getAsaasAccountId().isBlank();
    }

    private static boolean isApproved(String status) {
        return status != null && "APPROVED".equalsIgnoreCase(status.trim());
    }

    private static String normalizePhone(String phone) {
        return phone == null ? null : phone.replaceAll("\\D", "");
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private String resolveStatusMessage(
            Subaccount subaccount, AsaasOnboarding onboarding, boolean enabled) {
        if (enabled) {
            return null;
        }
        if (onboarding == null || onboarding.getStatus() == AsaasOnboardingStatus.NOT_STARTED) {
            return "Complete financial onboarding to use PIX and transfers";
        }
        if (onboarding.getLastErrorMessage() != null) {
            return onboarding.getLastErrorMessage();
        }
        return "Financial account setup in progress";
    }

    private List<AsaasWebhookConfigRequest> buildWebhookConfig(String webhookToken) {
        String webhookUrl = asaasProperties.getWebhookUrl();
        if (webhookUrl == null || webhookUrl.isBlank()) {
            if (WEBHOOK_SKIP_LOGGED.compareAndSet(false, true)) {
                log.info("ASAAS_WEBHOOK_URL not set — onboarding subaccounts created without inline webhooks");
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
}
