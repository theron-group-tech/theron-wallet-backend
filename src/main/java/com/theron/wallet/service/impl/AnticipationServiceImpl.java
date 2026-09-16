package com.theron.wallet.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theron.wallet.dto.asaas.AsaasAnticipationRequest;
import com.theron.wallet.dto.asaas.AsaasAnticipationResponse;
import com.theron.wallet.dto.request.CreateAnticipationRequest;
import com.theron.wallet.dto.response.AnticipationResponse;
import com.theron.wallet.entity.Account;
import com.theron.wallet.entity.Charge;
import com.theron.wallet.entity.ReceivableAnticipation;
import com.theron.wallet.enums.AnticipationStatus;
import com.theron.wallet.exception.ForbiddenException;
import com.theron.wallet.exception.InvalidRequestException;
import com.theron.wallet.exception.ResourceNotFoundException;
import com.theron.wallet.integration.AsaasAnticipationClient;
import com.theron.wallet.repository.AccountRepository;
import com.theron.wallet.repository.ChargeRepository;
import com.theron.wallet.repository.ReceivableAnticipationRepository;
import com.theron.wallet.security.Actor;
import com.theron.wallet.security.PermissionCodes;
import com.theron.wallet.security.ResourceAuthorization;
import com.theron.wallet.service.AccountAsaasGateway;
import com.theron.wallet.service.AnticipationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnticipationServiceImpl implements AnticipationService {

    private final ResourceAuthorization resourceAuthorization;
    private final AccountRepository accountRepository;
    private final ChargeRepository chargeRepository;
    private final ReceivableAnticipationRepository anticipationRepository;
    private final AccountAsaasGateway accountAsaasGateway;
    private final AsaasAnticipationClient asaasAnticipationClient;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public AnticipationResponse simulate(Actor actor, CreateAnticipationRequest request) {
        UUID accountId = resourceAuthorization.requireBoundAccount(actor, PermissionCodes.ANTICIPATIONS_CREATE);
        Account account = loadAccount(accountId);
        List<String> paymentIds = resolvePaymentIds(accountId, request);
        String apiKey = accountAsaasGateway.resolveApiKey(accountId);

        AsaasAnticipationResponse simulated = asaasAnticipationClient.simulate(
                apiKey, AsaasAnticipationRequest.builder().payment(paymentIds).build());

        return AnticipationResponse.builder()
                .organizationId(account.getOrganization().getId())
                .accountId(accountId)
                .status(AnticipationStatus.PENDING)
                .requestedValue(firstNonNull(simulated.getTotalValue(), simulated.getValue()))
                .netValue(simulated.getNetValue())
                .feeValue(simulated.getFeeValue())
                .paymentIds(paymentIds)
                .simulationJson(toJson(simulated))
                .build();
    }

    @Override
    @Transactional
    public AnticipationResponse create(Actor actor, CreateAnticipationRequest request) {
        UUID accountId = resourceAuthorization.requireBoundAccount(actor, PermissionCodes.ANTICIPATIONS_CREATE);
        Account account = loadAccount(accountId);
        List<String> paymentIds = resolvePaymentIds(accountId, request);
        String apiKey = accountAsaasGateway.resolveApiKey(accountId);

        AsaasAnticipationResponse created = asaasAnticipationClient.create(
                apiKey, AsaasAnticipationRequest.builder().payment(paymentIds).build());

        ReceivableAnticipation entity = ReceivableAnticipation.builder()
                .organization(account.getOrganization())
                .account(account)
                .asaasAnticipationId(created.getId())
                .status(mapStatus(created.getStatus()))
                .requestedValue(firstNonNull(created.getTotalValue(), created.getValue()))
                .netValue(created.getNetValue())
                .feeValue(created.getFeeValue())
                .paymentIdsJson(toJson(paymentIds))
                .simulationJson(toJson(created))
                .build();

        return toResponse(anticipationRepository.save(entity), paymentIds);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AnticipationResponse> list(Actor actor, Pageable pageable) {
        UUID accountId = resourceAuthorization.requireBoundAccount(actor, PermissionCodes.ANTICIPATIONS_READ);
        return anticipationRepository.findByAccount_IdOrderByCreatedAtDesc(accountId, pageable)
                .map(a -> toResponse(a, parsePaymentIds(a.getPaymentIdsJson())));
    }

    @Override
    @Transactional(readOnly = true)
    public AnticipationResponse get(Actor actor, UUID id) {
        UUID accountId = resourceAuthorization.requireBoundAccount(actor, PermissionCodes.ANTICIPATIONS_READ);
        ReceivableAnticipation entity = anticipationRepository.findByIdAndAccount_Id(id, accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Anticipation", "id", id));
        return toResponse(entity, parsePaymentIds(entity.getPaymentIdsJson()));
    }

    private Account loadAccount(UUID accountId) {
        return accountRepository.findByIdWithOrganization(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", "id", accountId));
    }

    private List<String> resolvePaymentIds(UUID accountId, CreateAnticipationRequest request) {
        List<String> paymentIds = new ArrayList<>();
        if (request.getChargeIds() != null) {
            for (UUID chargeId : request.getChargeIds()) {
                Charge charge = chargeRepository.findByIdAndAccount_Id(chargeId, accountId)
                        .orElseThrow(() -> new ForbiddenException("Charge does not belong to authenticated account"));
                if (!StringUtils.hasText(charge.getAsaasPaymentId())) {
                    throw new InvalidRequestException("Charge " + chargeId + " has no Asaas payment id");
                }
                paymentIds.add(charge.getAsaasPaymentId());
            }
        }
        if (request.getPaymentIds() != null) {
            for (String paymentId : request.getPaymentIds()) {
                if (!StringUtils.hasText(paymentId)) {
                    continue;
                }
                Charge charge = chargeRepository.findByAsaasPaymentId(paymentId.trim())
                        .orElseThrow(() -> new ForbiddenException("Payment does not belong to authenticated account"));
                if (!accountId.equals(charge.getAccount().getId())) {
                    throw new ForbiddenException("Payment does not belong to authenticated account");
                }
                paymentIds.add(paymentId.trim());
            }
        }
        if (paymentIds.isEmpty()) {
            throw new InvalidRequestException("chargeIds or paymentIds is required");
        }
        return paymentIds.stream().distinct().toList();
    }

    private AnticipationResponse toResponse(ReceivableAnticipation entity, List<String> paymentIds) {
        return AnticipationResponse.builder()
                .id(entity.getId())
                .organizationId(entity.getOrganization().getId())
                .accountId(entity.getAccount().getId())
                .asaasAnticipationId(entity.getAsaasAnticipationId())
                .status(entity.getStatus())
                .requestedValue(entity.getRequestedValue())
                .netValue(entity.getNetValue())
                .feeValue(entity.getFeeValue())
                .paymentIds(paymentIds)
                .simulationJson(entity.getSimulationJson())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private static AnticipationStatus mapStatus(String status) {
        if (status == null || status.isBlank()) {
            return AnticipationStatus.PENDING;
        }
        return switch (status.toUpperCase(Locale.ROOT)) {
            case "SCHEDULED" -> AnticipationStatus.SCHEDULED;
            case "CREDITED" -> AnticipationStatus.CREDITED;
            case "DEBITED" -> AnticipationStatus.DEBITED;
            case "CANCELLED", "CANCELED" -> AnticipationStatus.CANCELLED;
            default -> AnticipationStatus.PENDING;
        };
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return String.valueOf(value);
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> parsePaymentIds(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, List.class);
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private static java.math.BigDecimal firstNonNull(java.math.BigDecimal a, java.math.BigDecimal b) {
        return a != null ? a : b;
    }
}
