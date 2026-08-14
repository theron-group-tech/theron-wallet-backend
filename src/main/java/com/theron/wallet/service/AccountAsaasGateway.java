package com.theron.wallet.service;

import com.theron.wallet.entity.Subaccount;

import java.util.UUID;

/**
 * Resolves the Asaas Subaccount rail for a Theron Account.
 * Does not expose API keys.
 */
public interface AccountAsaasGateway {

    /**
     * Returns the linked Subaccount when Account is ACTIVE, Subaccount is eligible,
     * and an encrypted API key is stored. Otherwise throws InvalidRequestException (422).
     */
    Subaccount requireConfiguredSubaccount(UUID accountId);

    /**
     * Decrypts the Subaccount API key for outbound Asaas calls. Never log or return to clients.
     */
    String resolveApiKey(UUID accountId);
}
