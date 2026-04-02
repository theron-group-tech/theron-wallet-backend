package com.theron.wallet.security;

import com.theron.wallet.config.AsaasProperties;
import com.theron.wallet.entity.Subaccount;
import com.theron.wallet.exception.SubaccountOperationBlockedException;
import com.theron.wallet.repository.SubaccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * Resolves the correct Asaas API key for outbound operations based on the customer's
 * subaccount status. If the customer has an active subaccount, returns the subaccount's
 * decrypted key. If no subaccount exists, falls back to the root (platform) key.
 * If the subaccount exists but is blocked, rejects the operation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AsaasApiKeyResolver {

    private final SubaccountRepository subaccountRepository;
    private final ApiKeyEncryptionService apiKeyEncryptionService;
    private final AsaasProperties asaasProperties;

    /**
     * Resolves the API key to use for outbound Asaas operations (create charge, PIX, etc.).
     *
     * @param customerId the customer requesting the operation
     * @return the decrypted subaccount key, or the root platform key if no subaccount exists
     * @throws SubaccountOperationBlockedException if the subaccount exists but is not allowed to perform outbound operations
     */
    public String resolveForOutbound(UUID customerId) {
        Optional<Subaccount> subaccountOpt = subaccountRepository.findByCustomerId(customerId);

        if (subaccountOpt.isEmpty()) {
            log.debug("No subaccount for customer={}, using root key", customerId);
            return asaasProperties.getKey();
        }

        Subaccount subaccount = subaccountOpt.get();

        if (!subaccount.getStatus().allowsOutboundOperations()) {
            log.warn("Outbound operation blocked for customer={}, subaccountStatus={}",
                    customerId, subaccount.getStatus());
            throw new SubaccountOperationBlockedException(
                    String.format("Subaccount is %s — outbound operations are not allowed",
                            subaccount.getStatus()));
        }

        if (subaccount.getEncryptedApiKey() == null) {
            log.warn("Subaccount for customer={} has no API key, falling back to root key", customerId);
            return asaasProperties.getKey();
        }

        log.debug("Resolved subaccount key for customer={}, status={}", customerId, subaccount.getStatus());
        return apiKeyEncryptionService.decrypt(subaccount.getEncryptedApiKey());
    }
}
