package com.theron.wallet.service.impl;

import com.theron.wallet.entity.Account;
import com.theron.wallet.entity.Transaction;
import com.theron.wallet.entity.Wallet;
import com.theron.wallet.exception.DuplicateResourceException;
import com.theron.wallet.exception.ResourceNotFoundException;
import com.theron.wallet.repository.TransactionRepository;
import com.theron.wallet.service.IdempotencyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IdempotencyServiceImpl implements IdempotencyService {

    private final TransactionRepository transactionRepository;

    @Override
    public String resolveKey(String headerKey, String bodyKey) {
        if (headerKey != null && !headerKey.isBlank()) {
            return headerKey.trim();
        }
        if (bodyKey != null && !bodyKey.isBlank()) {
            return bodyKey.trim();
        }
        return UUID.randomUUID().toString();
    }

    @Override
    public String hash(String... parts) {
        String canonical = String.join("|", parts == null ? new String[0] : parts);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    @Override
    public String amountPart(BigDecimal amount) {
        if (amount == null) {
            return "";
        }
        return amount.setScale(2, RoundingMode.HALF_EVEN).toPlainString();
    }

    @Override
    @Transactional
    public Optional<Transaction> findExisting(String idempotencyKey, String requestHash) {
        Optional<Transaction> existing = transactionRepository.findByIdempotencyKeyForUpdate(idempotencyKey);
        existing.ifPresent(transaction -> assertSamePayload(transaction, requestHash));
        return existing;
    }

    @Override
    @Transactional
    public Transaction requireExisting(String idempotencyKey, String requestHash) {
        Transaction existing = transactionRepository.findByIdempotencyKeyForUpdate(idempotencyKey)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", "idempotencyKey", idempotencyKey));
        assertSamePayload(existing, requestHash);
        return existing;
    }

    @Override
    public void applyOwner(Transaction.TransactionBuilder builder, Wallet wallet) {
        builder.currency(wallet.getCurrency() != null ? wallet.getCurrency() : "BRL");
        Account account = wallet.getAccount();
        if (account != null) {
            builder.account(account).organization(account.getOrganization());
        }
    }

    @Override
    public void applyOwner(Transaction transaction, Wallet wallet) {
        transaction.setCurrency(wallet.getCurrency() != null ? wallet.getCurrency() : "BRL");
        Account account = wallet.getAccount();
        if (account != null) {
            transaction.setAccount(account);
            transaction.setOrganization(account.getOrganization());
        }
    }

    private static void assertSamePayload(Transaction existing, String requestHash) {
        if (existing.getRequestHash() != null
                && requestHash != null
                && !existing.getRequestHash().equals(requestHash)) {
            throw new DuplicateResourceException(
                    "Idempotency-Key already used with a different payload");
        }
    }
}
