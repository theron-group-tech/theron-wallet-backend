package com.theron.wallet.service.impl;

import com.theron.wallet.config.AsaasProperties;
import com.theron.wallet.dto.asaas.AsaasPaymentResponse;
import com.theron.wallet.dto.asaas.AsaasTransferResponse;
import com.theron.wallet.entity.AsaasReconciliation;
import com.theron.wallet.entity.Transaction;
import com.theron.wallet.enums.ReconciliationKind;
import com.theron.wallet.enums.ReconciliationStatus;
import com.theron.wallet.enums.TransactionStatus;
import com.theron.wallet.enums.TransactionType;
import com.theron.wallet.exception.AsaasApiException;
import com.theron.wallet.exception.ResourceNotFoundException;
import com.theron.wallet.integration.AsaasPaymentClient;
import com.theron.wallet.integration.AsaasTransferClient;
import com.theron.wallet.repository.AsaasReconciliationRepository;
import com.theron.wallet.repository.TransactionRepository;
import com.theron.wallet.security.AsaasApiKeyResolver;
import com.theron.wallet.service.ReconciliationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReconciliationServiceImpl implements ReconciliationService {

    private static final Set<String> ASAAS_COMPLETED = Set.of(
            "RECEIVED", "CONFIRMED", "DONE", "RECEIVED_IN_CASH");
    private static final Set<String> ASAAS_IN_FLIGHT = Set.of(
            "PENDING", "BANK_PROCESSING", "AWAITING_RISK_ANALYSIS");
    private static final Set<String> ASAAS_FAILED = Set.of(
            "FAILED", "OVERDUE", "REFUNDED", "BLOCKED",
            "CHARGEBACK_REQUESTED", "CHARGEBACK_DISPUTE");
    private static final Set<String> ASAAS_CANCELLED = Set.of("CANCELLED");

    private final TransactionRepository transactionRepository;
    private final AsaasReconciliationRepository asaasReconciliationRepository;
    private final AsaasPaymentClient asaasPaymentClient;
    private final AsaasTransferClient asaasTransferClient;
    private final AsaasApiKeyResolver asaasApiKeyResolver;
    private final AsaasProperties asaasProperties;

    @Override
    @Transactional
    public AsaasReconciliation reconcile(UUID transactionId) {
        Transaction local = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", "id", transactionId));
        ReconciliationKind kind = kindOf(local);
        String asaasId = local.getAsaasPaymentId();
        if (asaasId == null || asaasId.isBlank()) {
            return save(local, null, kind, ReconciliationStatus.FAILED,
                    local.getStatus() != null ? local.getStatus().name() : null,
                    null, local.getAmount(), null, "Theron without Asaas");
        }
        RemoteSnapshot remote = fetchRemote(kind, asaasId, resolveApiKey(local));
        return compare(local, remote, kind, asaasId);
    }

    @Override
    @Transactional
    public AsaasReconciliation reconcileAsaasResource(String asaasId, ReconciliationKind kind) {
        Transaction local = transactionRepository.findByAsaasPaymentId(asaasId).orElse(null);
        if (local == null) {
            RemoteSnapshot remote = fetchRemote(kind, asaasId, asaasProperties.getKey());
            return save(null, asaasId, kind, ReconciliationStatus.FAILED,
                    null,
                    remote != null ? remote.status() : null,
                    null,
                    remote != null ? remote.amount() : null,
                    "Asaas without Theron");
        }
        return reconcile(local.getId());
    }

    private AsaasReconciliation compare(
            Transaction local, RemoteSnapshot remote, ReconciliationKind kind, String asaasId) {
        String localStatus = local.getStatus() != null ? local.getStatus().name() : null;
        if (remote == null) {
            return save(local, asaasId, kind, ReconciliationStatus.FAILED,
                    localStatus, null, local.getAmount(), null, "Theron without Asaas");
        }
        Lane localLane = mapLocal(local.getStatus());
        Lane remoteLane = mapAsaas(remote.status());
        boolean amountsMatch = amountsEqual(local.getAmount(), remote.amount());
        if (!amountsMatch || localLane != remoteLane) {
            return save(local, asaasId, kind, ReconciliationStatus.DIVERGENT,
                    localStatus, remote.status(), local.getAmount(), remote.amount(),
                    "Value or mapped status differs");
        }
        if (localLane == Lane.IN_FLIGHT) {
            return save(local, asaasId, kind, ReconciliationStatus.PENDING,
                    localStatus, remote.status(), local.getAmount(), remote.amount(),
                    "Awaiting Asaas confirmation");
        }
        return save(local, asaasId, kind, ReconciliationStatus.MATCHED,
                localStatus, remote.status(), local.getAmount(), remote.amount(),
                "Local and Asaas agree");
    }

    private RemoteSnapshot fetchRemote(ReconciliationKind kind, String asaasId, String apiKey) {
        try {
            if (kind == ReconciliationKind.TRANSFER) {
                AsaasTransferResponse transfer = asaasTransferClient.retrieveTransfer(apiKey, asaasId);
                if (transfer == null) {
                    return null;
                }
                return new RemoteSnapshot(transfer.getStatus(), transfer.getValue());
            }
            AsaasPaymentResponse payment = asaasPaymentClient.retrievePayment(apiKey, asaasId);
            if (payment == null) {
                return null;
            }
            return new RemoteSnapshot(payment.getStatus(), payment.getValue());
        } catch (AsaasApiException ex) {
            if (ex.getAsaasStatusCode() == 404) {
                return null;
            }
            throw ex;
        }
    }

    private AsaasReconciliation save(
            Transaction local,
            String asaasId,
            ReconciliationKind kind,
            ReconciliationStatus status,
            String localStatus,
            String asaasStatus,
            BigDecimal localAmount,
            BigDecimal asaasAmount,
            String detail) {
        AsaasReconciliation row = AsaasReconciliation.builder()
                .transaction(local)
                .asaasId(asaasId != null ? asaasId : (local != null ? local.getAsaasPaymentId() : null))
                .kind(kind)
                .status(status)
                .localStatus(localStatus)
                .asaasStatus(asaasStatus)
                .localAmount(localAmount)
                .asaasAmount(asaasAmount)
                .detail(detail)
                .build();
        return asaasReconciliationRepository.save(row);
    }

    private String resolveApiKey(Transaction transaction) {
        try {
            if (transaction.getWallet() != null && transaction.getWallet().getSubaccount() != null) {
                String key = asaasApiKeyResolver.resolveForSubaccount(
                        transaction.getWallet().getSubaccount().getId());
                if (key != null && !key.isBlank()) {
                    return key;
                }
            }
        } catch (RuntimeException ex) {
            log.debug("Falling back to root Asaas key for reconciliation: {}", ex.getMessage());
        }
        return asaasProperties.getKey();
    }

    private static ReconciliationKind kindOf(Transaction transaction) {
        TransactionType type = transaction.getType();
        if (type == TransactionType.DEPOSIT || type == TransactionType.PAYMENT) {
            return ReconciliationKind.PAYMENT;
        }
        return ReconciliationKind.TRANSFER;
    }

    private static Lane mapLocal(TransactionStatus status) {
        if (status == null) {
            return Lane.OTHER;
        }
        return switch (status) {
            case PENDING, PENDING_APPROVAL, PROCESSING -> Lane.IN_FLIGHT;
            case COMPLETED -> Lane.COMPLETED;
            case FAILED, REVERSED -> Lane.FAILED;
            case CANCELLED -> Lane.CANCELLED;
        };
    }

    private static Lane mapAsaas(String status) {
        if (status == null) {
            return Lane.OTHER;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (ASAAS_COMPLETED.contains(normalized)) {
            return Lane.COMPLETED;
        }
        if (ASAAS_IN_FLIGHT.contains(normalized)) {
            return Lane.IN_FLIGHT;
        }
        if (ASAAS_FAILED.contains(normalized)) {
            return Lane.FAILED;
        }
        if (ASAAS_CANCELLED.contains(normalized)) {
            return Lane.CANCELLED;
        }
        return Lane.OTHER;
    }

    private static boolean amountsEqual(BigDecimal left, BigDecimal right) {
        if (left == null || right == null) {
            return left == null && right == null;
        }
        return left.compareTo(right) == 0;
    }

    private enum Lane {
        IN_FLIGHT,
        COMPLETED,
        FAILED,
        CANCELLED,
        OTHER
    }

    private record RemoteSnapshot(String status, BigDecimal amount) {
    }
}
