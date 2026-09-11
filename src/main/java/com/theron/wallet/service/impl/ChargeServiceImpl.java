package com.theron.wallet.service.impl;

import com.theron.wallet.dto.asaas.AsaasCreateCustomerRequest;
import com.theron.wallet.dto.asaas.AsaasCreateCustomerResponse;
import com.theron.wallet.dto.asaas.AsaasPaymentRequest;
import com.theron.wallet.dto.asaas.AsaasPaymentResponse;
import com.theron.wallet.dto.asaas.AsaasSplitItem;
import com.theron.wallet.dto.request.CreateChargeRequest;
import com.theron.wallet.dto.response.ChargeResponse;
import com.theron.wallet.entity.Account;
import com.theron.wallet.entity.BillingCustomer;
import com.theron.wallet.entity.Charge;
import com.theron.wallet.entity.ChargeInstallment;
import com.theron.wallet.entity.ChargeSplit;
import com.theron.wallet.entity.Organization;
import com.theron.wallet.entity.Subaccount;
import com.theron.wallet.entity.Transaction;
import com.theron.wallet.entity.Wallet;
import com.theron.wallet.enums.ChargeBillingType;
import com.theron.wallet.enums.ChargeSplitRole;
import com.theron.wallet.enums.ChargeStatus;
import com.theron.wallet.enums.TransactionStatus;
import com.theron.wallet.enums.TransactionType;
import com.theron.wallet.exception.InvalidRequestException;
import com.theron.wallet.exception.ResourceNotFoundException;
import com.theron.wallet.integration.AsaasCustomerClient;
import com.theron.wallet.integration.AsaasPaymentClient;
import com.theron.wallet.repository.AccountRepository;
import com.theron.wallet.repository.BillingCustomerRepository;
import com.theron.wallet.repository.ChargeRepository;
import com.theron.wallet.repository.TransactionRepository;
import com.theron.wallet.repository.WalletRepository;
import com.theron.wallet.security.Actor;
import com.theron.wallet.security.PermissionCodes;
import com.theron.wallet.security.ResourceAuthorization;
import com.theron.wallet.service.AccountAsaasGateway;
import com.theron.wallet.service.ChargeService;
import com.theron.wallet.service.PlatformSplitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChargeServiceImpl implements ChargeService {

    private static final DateTimeFormatter ASAAS_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final ResourceAuthorization resourceAuthorization;
    private final AccountRepository accountRepository;
    private final WalletRepository walletRepository;
    private final BillingCustomerRepository billingCustomerRepository;
    private final ChargeRepository chargeRepository;
    private final TransactionRepository transactionRepository;
    private final AccountAsaasGateway accountAsaasGateway;
    private final AsaasCustomerClient asaasCustomerClient;
    private final AsaasPaymentClient asaasPaymentClient;
    private final PlatformSplitService platformSplitService;

    @Override
    @Transactional
    public ChargeResponse create(Actor actor, CreateChargeRequest request) {
        UUID accountId = resourceAuthorization.requireBoundAccount(actor, PermissionCodes.CHARGES_CREATE);
        Account account = accountRepository.findByIdWithOrganization(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", "id", accountId));
        Organization organization = account.getOrganization();

        if (StringUtils.hasText(request.getExternalReference())) {
            var existing = chargeRepository.findByAccount_IdAndExternalReference(
                    accountId, request.getExternalReference().trim());
            if (existing.isPresent()) {
                log.info("Idempotent charge hit: accountId={}, externalReference={}",
                        accountId, request.getExternalReference());
                return toResponse(existing.get());
            }
        }

        validateCreate(request);

        Subaccount subaccount = accountAsaasGateway.requireConfiguredSubaccount(accountId);
        String apiKey = accountAsaasGateway.resolveApiKey(accountId);

        BillingCustomer billingCustomer = resolveOrCreateCustomer(account, organization, apiKey, request.getCustomer());

        List<AsaasSplitItem> counterpartySplits = buildCounterpartySplits(request.getSplit());
        AsaasPaymentRequest paymentRequest = AsaasPaymentRequest.builder()
                .customer(billingCustomer.getAsaasCustomerId())
                .billingType(request.getBillingType().name())
                .value(request.getValue())
                .dueDate(request.getDueDate().format(ASAAS_DATE))
                .description(request.getDescription())
                .externalReference(StringUtils.hasText(request.getExternalReference())
                        ? request.getExternalReference().trim() : null)
                .split(counterpartySplits.isEmpty() ? null : new ArrayList<>(counterpartySplits))
                .build();

        int installmentCount = request.getInstallments() != null && request.getInstallments() > 1
                ? request.getInstallments() : 1;
        if (installmentCount > 1) {
            if (request.getBillingType() != ChargeBillingType.CREDIT_CARD) {
                throw new InvalidRequestException("Installments are only supported for CREDIT_CARD");
            }
            paymentRequest.setInstallmentCount(installmentCount);
            paymentRequest.setInstallmentValue(request.getValue()
                    .divide(BigDecimal.valueOf(installmentCount), 2, RoundingMode.HALF_UP));
        }

        // Platform fee split is appended; Asaas fees remain on the issuing account.
        platformSplitService.applyToPayment(paymentRequest);

        String idempotencyKey = StringUtils.hasText(request.getExternalReference())
                ? "charge:" + accountId + ":" + request.getExternalReference().trim()
                : "charge:" + UUID.randomUUID();

        AsaasPaymentResponse paymentResponse =
                asaasPaymentClient.createPayment(apiKey, paymentRequest, idempotencyKey);

        Wallet wallet = walletRepository.findByAccount_Id(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet", "accountId", accountId));

        Transaction transaction = Transaction.builder()
                .wallet(wallet)
                .organization(organization)
                .account(account)
                .createdBy(account.getOwnerUser())
                .type(TransactionType.DEPOSIT)
                .status(TransactionStatus.PENDING)
                .amount(request.getValue())
                .currency("BRL")
                .description(request.getDescription() != null ? request.getDescription() : "Charge")
                .asaasPaymentId(paymentResponse.getId())
                .externalReference(paymentRequest.getExternalReference())
                .idempotencyKey(idempotencyKey)
                .build();
        transaction = transactionRepository.save(transaction);

        Charge charge = Charge.builder()
                .organization(organization)
                .account(account)
                .subaccount(subaccount)
                .billingCustomer(billingCustomer)
                .transaction(transaction)
                .asaasPaymentId(paymentResponse.getId())
                .billingType(request.getBillingType())
                .value(request.getValue())
                .netValue(paymentResponse.getNetValue())
                .description(request.getDescription())
                .externalReference(paymentRequest.getExternalReference())
                .dueDate(request.getDueDate())
                .status(mapAsaasStatus(paymentResponse.getStatus()))
                .installmentCount(installmentCount)
                .invoiceUrl(paymentResponse.getInvoiceUrl())
                .bankSlipUrl(paymentResponse.getBankSlipUrl())
                .installments(new ArrayList<>())
                .splits(new ArrayList<>())
                .build();

        if (installmentCount > 1) {
            BigDecimal installmentValue = paymentRequest.getInstallmentValue();
            for (int i = 1; i <= installmentCount; i++) {
                charge.getInstallments().add(ChargeInstallment.builder()
                        .charge(charge)
                        .installmentNumber(i)
                        .value(installmentValue)
                        .dueDate(request.getDueDate().plusMonths(i - 1L))
                        .status(ChargeStatus.PENDING)
                        .build());
            }
        }

        persistSplits(charge, paymentRequest.getSplit(), counterpartySplits);

        Charge saved = chargeRepository.save(charge);
        log.info("Charge created: id={}, asaasPaymentId={}, accountId={}",
                saved.getId(), saved.getAsaasPaymentId(), accountId);
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ChargeResponse> list(Actor actor, Pageable pageable) {
        UUID accountId = resourceAuthorization.requireBoundAccount(actor, PermissionCodes.CHARGES_READ);
        return chargeRepository.findByAccount_IdOrderByCreatedAtDesc(accountId, pageable).map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public ChargeResponse get(Actor actor, UUID chargeId) {
        UUID accountId = resourceAuthorization.requireBoundAccount(actor, PermissionCodes.CHARGES_READ);
        Charge charge = chargeRepository.findByIdAndAccountIdWithDetails(chargeId, accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Charge", "id", chargeId));
        return toResponse(charge);
    }

    @Override
    @Transactional
    public ChargeResponse cancel(Actor actor, UUID chargeId) {
        UUID accountId = resourceAuthorization.requireBoundAccount(actor, PermissionCodes.CHARGES_CANCEL);
        Charge charge = chargeRepository.findByIdAndAccount_Id(chargeId, accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Charge", "id", chargeId));

        if (charge.getStatus() == ChargeStatus.CANCELLED || charge.getStatus() == ChargeStatus.DELETED) {
            return toResponse(charge);
        }
        if (charge.getStatus() == ChargeStatus.RECEIVED || charge.getStatus() == ChargeStatus.CONFIRMED) {
            throw new InvalidRequestException("Cannot cancel a received/confirmed charge");
        }

        if (StringUtils.hasText(charge.getAsaasPaymentId())) {
            String apiKey = accountAsaasGateway.resolveApiKey(accountId);
            asaasPaymentClient.deletePayment(apiKey, charge.getAsaasPaymentId());
        }

        charge.setStatus(ChargeStatus.CANCELLED);
        if (charge.getTransaction() != null
                && charge.getTransaction().getStatus() == TransactionStatus.PENDING) {
            charge.getTransaction().setStatus(TransactionStatus.CANCELLED);
        }
        return toResponse(chargeRepository.save(charge));
    }

    private void validateCreate(CreateChargeRequest request) {
        if (request.getCustomer() == null || !StringUtils.hasText(request.getCustomer().getCpfCnpj())) {
            throw new InvalidRequestException("customer.cpfCnpj is required");
        }
        String digits = request.getCustomer().getCpfCnpj().replaceAll("\\D", "");
        if (digits.length() != 11 && digits.length() != 14) {
            throw new InvalidRequestException("customer.cpfCnpj must be CPF (11) or CNPJ (14)");
        }
        request.getCustomer().setCpfCnpj(digits);
    }

    private BillingCustomer resolveOrCreateCustomer(
            Account account,
            Organization organization,
            String apiKey,
            CreateChargeRequest.ChargeCustomerRequest customerReq) {
        return billingCustomerRepository.findByAccount_IdAndCpfCnpj(account.getId(), customerReq.getCpfCnpj())
                .map(existing -> {
                    if (!StringUtils.hasText(existing.getAsaasCustomerId())) {
                        AsaasCreateCustomerResponse created = asaasCustomerClient.createCustomer(apiKey,
                                AsaasCreateCustomerRequest.builder()
                                        .name(customerReq.getName())
                                        .cpfCnpj(customerReq.getCpfCnpj())
                                        .email(customerReq.getEmail())
                                        .phone(customerReq.getPhone())
                                        .notificationDisabled(true)
                                        .build());
                        existing.setAsaasCustomerId(created.getId());
                        existing.setName(customerReq.getName());
                        existing.setEmail(customerReq.getEmail());
                        existing.setPhone(customerReq.getPhone());
                        return billingCustomerRepository.save(existing);
                    }
                    return existing;
                })
                .orElseGet(() -> {
                    AsaasCreateCustomerResponse created = asaasCustomerClient.createCustomer(apiKey,
                            AsaasCreateCustomerRequest.builder()
                                    .name(customerReq.getName())
                                    .cpfCnpj(customerReq.getCpfCnpj())
                                    .email(customerReq.getEmail())
                                    .phone(customerReq.getPhone())
                                    .notificationDisabled(true)
                                    .build());
                    return billingCustomerRepository.save(BillingCustomer.builder()
                            .organization(organization)
                            .account(account)
                            .asaasCustomerId(created.getId())
                            .name(customerReq.getName())
                            .cpfCnpj(customerReq.getCpfCnpj())
                            .email(customerReq.getEmail())
                            .phone(customerReq.getPhone())
                            .build());
                });
    }

    private List<AsaasSplitItem> buildCounterpartySplits(List<CreateChargeRequest.ChargeSplitRequest> splits) {
        if (splits == null || splits.isEmpty()) {
            return List.of();
        }
        List<AsaasSplitItem> items = new ArrayList<>();
        for (CreateChargeRequest.ChargeSplitRequest split : splits) {
            if (!StringUtils.hasText(split.getWalletId())) {
                throw new InvalidRequestException("split.walletId is required");
            }
            boolean hasPercent = split.getPercentualValue() != null
                    && split.getPercentualValue().compareTo(BigDecimal.ZERO) > 0;
            boolean hasFixed = split.getFixedValue() != null
                    && split.getFixedValue().compareTo(BigDecimal.ZERO) > 0;
            if (!hasPercent && !hasFixed) {
                throw new InvalidRequestException("split requires percentualValue or fixedValue");
            }
            items.add(AsaasSplitItem.builder()
                    .walletId(split.getWalletId().trim())
                    .percentualValue(hasPercent ? split.getPercentualValue() : null)
                    .fixedValue(hasFixed ? split.getFixedValue() : null)
                    .build());
        }
        return items;
    }

    private void persistSplits(Charge charge, List<AsaasSplitItem> finalSplits, List<AsaasSplitItem> counterparty) {
        if (finalSplits == null) {
            return;
        }
        for (AsaasSplitItem item : finalSplits) {
            boolean isCounterparty = counterparty.stream()
                    .anyMatch(c -> c.getWalletId().equals(item.getWalletId())
                            && valuesMatch(c.getFixedValue(), item.getFixedValue())
                            && valuesMatch(c.getPercentualValue(), item.getPercentualValue()));
            charge.getSplits().add(ChargeSplit.builder()
                    .charge(charge)
                    .walletId(item.getWalletId())
                    .percentualValue(item.getPercentualValue())
                    .fixedValue(item.getFixedValue())
                    .role(isCounterparty ? ChargeSplitRole.COUNTERPARTY : ChargeSplitRole.PLATFORM)
                    .build());
        }
    }

    private static boolean valuesMatch(BigDecimal a, BigDecimal b) {
        if (a == null && b == null) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return a.compareTo(b) == 0;
    }

    static ChargeStatus mapAsaasStatus(String status) {
        if (status == null || status.isBlank()) {
            return ChargeStatus.PENDING;
        }
        return switch (status.toUpperCase(Locale.ROOT)) {
            case "RECEIVED", "RECEIVED_IN_CASH" -> ChargeStatus.RECEIVED;
            case "CONFIRMED" -> ChargeStatus.CONFIRMED;
            case "OVERDUE" -> ChargeStatus.OVERDUE;
            case "REFUNDED" -> ChargeStatus.REFUNDED;
            case "DELETED" -> ChargeStatus.DELETED;
            case "PENDING" -> ChargeStatus.PENDING;
            default -> ChargeStatus.PENDING;
        };
    }

    private ChargeResponse toResponse(Charge charge) {
        List<ChargeResponse.SplitItem> splits = charge.getSplits() == null ? List.of()
                : charge.getSplits().stream()
                .map(s -> ChargeResponse.SplitItem.builder()
                        .walletId(s.getWalletId())
                        .percentualValue(s.getPercentualValue())
                        .fixedValue(s.getFixedValue())
                        .role(s.getRole())
                        .build())
                .toList();
        BillingCustomer customer = charge.getBillingCustomer();
        return ChargeResponse.builder()
                .id(charge.getId())
                .organizationId(charge.getOrganization().getId())
                .accountId(charge.getAccount().getId())
                .asaasPaymentId(charge.getAsaasPaymentId())
                .billingType(charge.getBillingType())
                .value(charge.getValue())
                .netValue(charge.getNetValue())
                .description(charge.getDescription())
                .externalReference(charge.getExternalReference())
                .dueDate(charge.getDueDate())
                .status(charge.getStatus())
                .installmentCount(charge.getInstallmentCount())
                .invoiceUrl(charge.getInvoiceUrl())
                .bankSlipUrl(charge.getBankSlipUrl())
                .billingCustomerId(customer != null ? customer.getId() : null)
                .customerName(customer != null ? customer.getName() : null)
                .customerCpfCnpj(customer != null ? customer.getCpfCnpj() : null)
                .splits(splits)
                .createdAt(charge.getCreatedAt())
                .updatedAt(charge.getUpdatedAt())
                .build();
    }
}
