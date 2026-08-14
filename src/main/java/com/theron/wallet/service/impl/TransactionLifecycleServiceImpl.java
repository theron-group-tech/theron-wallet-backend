package com.theron.wallet.service.impl;

import com.theron.wallet.entity.Transaction;
import com.theron.wallet.enums.TransactionStatus;
import com.theron.wallet.exception.InvalidRequestException;
import com.theron.wallet.repository.TransactionRepository;
import com.theron.wallet.service.TransactionLifecycleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class TransactionLifecycleServiceImpl implements TransactionLifecycleService {

    private static final Map<TransactionStatus, Set<TransactionStatus>> ALLOWED = Map.of(
            TransactionStatus.PENDING, Set.of(
                    TransactionStatus.PENDING_APPROVAL,
                    TransactionStatus.PROCESSING,
                    TransactionStatus.COMPLETED,
                    TransactionStatus.FAILED,
                    TransactionStatus.CANCELLED),
            TransactionStatus.PENDING_APPROVAL, Set.of(
                    TransactionStatus.PROCESSING,
                    TransactionStatus.CANCELLED,
                    TransactionStatus.FAILED),
            TransactionStatus.PROCESSING, Set.of(
                    TransactionStatus.COMPLETED, TransactionStatus.FAILED, TransactionStatus.CANCELLED),
            TransactionStatus.COMPLETED, Set.of(TransactionStatus.REVERSED)
    );

    private final TransactionRepository transactionRepository;

    @Override
    @Transactional
    public Transaction transition(Transaction transaction, TransactionStatus target) {
        if (target == null) {
            throw new InvalidRequestException("Transaction status is required");
        }
        TransactionStatus current = transaction.getStatus();
        if (current == target) {
            return transaction;
        }
        Set<TransactionStatus> allowed = ALLOWED.getOrDefault(current, Set.of());
        if (!allowed.contains(target)) {
            throw new InvalidRequestException(
                    String.format("Invalid status transition: %s → %s", current, target));
        }
        transaction.setStatus(target);
        if (target == TransactionStatus.COMPLETED || target == TransactionStatus.REVERSED) {
            transaction.setCompletedAt(LocalDateTime.now());
        }
        return transactionRepository.save(transaction);
    }
}
