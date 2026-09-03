package com.theron.wallet.service;

import com.theron.wallet.entity.Subaccount;
import com.theron.wallet.entity.Transaction;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Credits Account wallet for inbound Asaas PIX (Cobrar / chave / reconcile / Theron→Theron pay).
 * Idempotency key: {@code asaas:pix:in:{paymentOrResourceId}}.
 */
public interface InboundPixCreditService {

    /**
     * @return the credited (or already credited) transaction; empty if amount invalid
     */
    Optional<Transaction> credit(
            Subaccount destination,
            String paymentOrResourceId,
            BigDecimal amount,
            String description,
            String reference);

    static String idempotencyKey(String paymentOrResourceId) {
        return "asaas:pix:in:" + paymentOrResourceId;
    }
}
