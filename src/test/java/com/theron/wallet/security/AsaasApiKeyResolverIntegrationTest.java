package com.theron.wallet.security;

import com.theron.wallet.integration.AsaasPaymentClient;
import com.theron.wallet.integration.AsaasSubaccountClient;
import com.theron.wallet.integration.AsaasTransferClient;
import com.theron.wallet.integration.AsaasWebhookClient;
import com.theron.wallet.repository.SubaccountApiKeyAuditRepository;
import com.theron.wallet.repository.SubaccountRepository;
import com.theron.wallet.repository.TransactionRepository;
import com.theron.wallet.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for AsaasApiKeyResolver.
 *
 * Since V9, subaccounts are standalone entities — they are NOT linked to Customers.
 * The resolver always returns the platform root API key for all outbound operations.
 *
 * NOTE: Does NOT extend BaseIntegrationTest intentionally — BaseIntegrationTest
 * replaces AsaasApiKeyResolver with a @MockitoBean, which would prevent testing
 * the real implementation here.
 */
@SpringBootTest
@ActiveProfiles("test")
class AsaasApiKeyResolverIntegrationTest {

    // Mock external HTTP clients to prevent real HTTP calls
    @MockitoBean
    private AsaasPaymentClient asaasPaymentClient;
    @MockitoBean
    private AsaasSubaccountClient asaasSubaccountClient;
    @MockitoBean
    private AsaasTransferClient asaasTransferClient;
    @MockitoBean
    private AsaasWebhookClient asaasWebhookClient;

    @Autowired
    private AsaasApiKeyResolver asaasApiKeyResolver;

    @Autowired
    private SubaccountApiKeyAuditRepository auditRepository;
    @Autowired
    private SubaccountRepository subaccountRepository;
    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private WalletRepository walletRepository;

    @Value("${asaas.api.key}")
    private String rootApiKey;

    @BeforeEach
    void cleanDatabase() {
        auditRepository.deleteAll();
        subaccountRepository.deleteAll();
        transactionRepository.deleteAll();
        walletRepository.deleteAll();
    }

    @Test
    @DisplayName("should always return platform root key regardless of customerId")
    void shouldAlwaysReturnRootKey() {
        String resolved = asaasApiKeyResolver.resolveForOutbound(UUID.randomUUID());

        assertThat(resolved).isEqualTo(rootApiKey);
    }

    @Test
    @DisplayName("should return platform root key even when no subaccounts exist")
    void shouldReturnRootKeyWhenNoSubaccountsExist() {
        // DB is clean from cleanDatabase()
        String resolved = asaasApiKeyResolver.resolveForOutbound(UUID.randomUUID());

        assertThat(resolved).isNotBlank();
        assertThat(resolved).isEqualTo(rootApiKey);
    }
}
