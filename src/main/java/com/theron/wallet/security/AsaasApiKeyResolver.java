package com.theron.wallet.security;

import com.theron.wallet.config.AsaasProperties;
import com.theron.wallet.entity.Subaccount;
import com.theron.wallet.exception.ResourceNotFoundException;
import com.theron.wallet.repository.SubaccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Resolves the Asaas API key for outbound operations.
 *
 * Subaccounts are now standalone entities — they are NOT linked to Customers.
 * All outbound operations (charges, transfers) use the platform root key.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AsaasApiKeyResolver {

    private final AsaasProperties asaasProperties;
    private final SubaccountRepository subaccountRepository;
    private final ApiKeyEncryptionService encryptionService;

    /**
     * Resolves the Asaas API key for a specific subaccount.
     * Decrypts the stored encryptedApiKey and returns the plaintext key.
     *
     * @throws ResourceNotFoundException if subaccount not found or has no API key stored
     */
    public String resolveForSubaccount(UUID subaccountId) {
        Subaccount subaccount = subaccountRepository.findById(subaccountId)
                .orElseThrow(() -> new ResourceNotFoundException("Subaccount", "id", subaccountId));

        if (subaccount.getEncryptedApiKey() == null) {
            throw new ResourceNotFoundException(
                    "Subaccount " + subaccountId + " has no Asaas API key stored. "
                    + "The subaccount may still be in PROVISIONING or FAILED state.");
        }

        log.debug("Resolving Asaas API key for subaccount={}", subaccountId);
        return encryptionService.decrypt(subaccount.getEncryptedApiKey());
    }

    /**
     * Returns the platform root API key.
     * Use only for subaccount provisioning (POST /v3/accounts).
     *
     * @param customerId retained in the signature for backward compatibility
     */
    public String resolveForOutbound(UUID customerId) {
        log.debug("Resolving platform root Asaas API key");
        return asaasProperties.getKey();
    }
}
