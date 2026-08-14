package com.theron.wallet.service;

import com.theron.wallet.entity.Transaction;
import com.theron.wallet.entity.Wallet;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface IdempotencyService {

    String resolveKey(String headerKey, String bodyKey);

    String hash(String... parts);

    String amountPart(BigDecimal amount);

    Optional<Transaction> findExisting(String idempotencyKey, String requestHash);

    Transaction requireExisting(String idempotencyKey, String requestHash);

    void applyOwner(Transaction.TransactionBuilder builder, Wallet wallet);

    void applyOwner(Transaction transaction, Wallet wallet);
}
