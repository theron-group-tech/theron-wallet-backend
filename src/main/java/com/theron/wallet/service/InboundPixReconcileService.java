package com.theron.wallet.service;

import com.theron.wallet.dto.response.InboundReconcileResponse;

import java.util.UUID;

public interface InboundPixReconcileService {

    /**
     * Lists RECEIVED/CONFIRMED payments on the account's Asaas subaccount and credits orphans
     * onto the Account wallet (idempotent via {@code asaas:pix:in:{paymentId}}).
     */
    InboundReconcileResponse reconcileAccount(UUID accountId);
}
