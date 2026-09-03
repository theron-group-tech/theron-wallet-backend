package com.theron.wallet.service;

import com.theron.wallet.dto.response.SubaccountWebhookRepairResponse;

import java.util.UUID;

public interface AsaasSubaccountWebhookService {

    /**
     * Registers (or re-registers) PAYMENT_* / TRANSFER_* webhooks on the Asaas subaccount
     * using the stored apiKey and {@code subaccount.webhookToken} as authToken.
     */
    SubaccountWebhookRepairResponse repairForSubaccount(UUID subaccountId);

    /**
     * Repairs webhooks for all ACTIVE subaccounts that have an encrypted API key.
     */
    SubaccountWebhookRepairResponse repairAllActive();
}
