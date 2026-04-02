package com.theron.wallet.security;

import com.theron.wallet.TestFixtures;
import com.theron.wallet.entity.Customer;
import com.theron.wallet.entity.Subaccount;
import com.theron.wallet.enums.SubaccountStatus;
import com.theron.wallet.exception.SubaccountOperationBlockedException;
import com.theron.wallet.integration.AsaasCustomerClient;
import com.theron.wallet.integration.AsaasPaymentClient;
import com.theron.wallet.integration.AsaasSubaccountClient;
import com.theron.wallet.integration.AsaasTransferClient;
import com.theron.wallet.integration.AsaasWebhookClient;
import com.theron.wallet.repository.CustomerRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test for AsaasApiKeyResolver using real DB and encryption,
 * but mocking external Asaas HTTP clients.
 */
@SpringBootTest
@ActiveProfiles("test")
class AsaasApiKeyResolverIntegrationTest {

    @MockitoBean
    private AsaasCustomerClient asaasCustomerClient;

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
    private CustomerRepository customerRepository;

    @Autowired
    private SubaccountRepository subaccountRepository;

    @Autowired
    private SubaccountApiKeyAuditRepository auditRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private ApiKeyEncryptionService apiKeyEncryptionService;

    @Value("${asaas.api.key}")
    private String rootApiKey;

    private Customer savedCustomer;

    @BeforeEach
    void setUp() {
        auditRepository.deleteAll();
        subaccountRepository.deleteAll();
        transactionRepository.deleteAll();
        walletRepository.deleteAll();
        customerRepository.deleteAll();
        savedCustomer = customerRepository.save(TestFixtures.aCustomer());
    }

    @Test
    @DisplayName("should return root key when customer has no subaccount")
    void shouldReturnRootKeyWhenNoSubaccount() {
        String resolved = asaasApiKeyResolver.resolveForOutbound(savedCustomer.getId());

        assertThat(resolved).isEqualTo(rootApiKey);
    }

    @Test
    @DisplayName("should return subaccount key when subaccount is ACTIVE")
    void shouldReturnSubaccountKeyWhenActive() {
        String plainKey = "sub_active_key_" + savedCustomer.getId();
        Subaccount subaccount = TestFixtures.aSubaccount(savedCustomer, SubaccountStatus.ACTIVE);
        subaccount.setEncryptedApiKey(apiKeyEncryptionService.encrypt(plainKey));
        subaccountRepository.save(subaccount);

        String resolved = asaasApiKeyResolver.resolveForOutbound(savedCustomer.getId());

        assertThat(resolved).isEqualTo(plainKey);
    }

    @Test
    @DisplayName("should return subaccount key when subaccount is PENDING_EVALUATION")
    void shouldReturnSubaccountKeyWhenPendingEvaluation() {
        String plainKey = "sub_pending_key_" + savedCustomer.getId();
        Subaccount subaccount = TestFixtures.aSubaccount(savedCustomer, SubaccountStatus.PENDING_EVALUATION);
        subaccount.setEncryptedApiKey(apiKeyEncryptionService.encrypt(plainKey));
        subaccountRepository.save(subaccount);

        String resolved = asaasApiKeyResolver.resolveForOutbound(savedCustomer.getId());

        assertThat(resolved).isEqualTo(plainKey);
    }

    @Test
    @DisplayName("should throw when subaccount is EVALUATION_BLOCKED")
    void shouldThrowWhenEvaluationBlocked() {
        Subaccount subaccount = TestFixtures.aSubaccount(savedCustomer, SubaccountStatus.EVALUATION_BLOCKED);
        subaccount.setEncryptedApiKey(apiKeyEncryptionService.encrypt("blocked_key"));
        subaccountRepository.save(subaccount);

        assertThatThrownBy(() -> asaasApiKeyResolver.resolveForOutbound(savedCustomer.getId()))
                .isInstanceOf(SubaccountOperationBlockedException.class)
                .hasMessageContaining("EVALUATION_BLOCKED");
    }

    @Test
    @DisplayName("should throw when subaccount is SUSPENDED")
    void shouldThrowWhenSuspended() {
        Subaccount subaccount = TestFixtures.aSubaccount(savedCustomer, SubaccountStatus.SUSPENDED);
        subaccount.setEncryptedApiKey(apiKeyEncryptionService.encrypt("suspended_key"));
        subaccountRepository.save(subaccount);

        assertThatThrownBy(() -> asaasApiKeyResolver.resolveForOutbound(savedCustomer.getId()))
                .isInstanceOf(SubaccountOperationBlockedException.class)
                .hasMessageContaining("SUSPENDED");
    }

    @Test
    @DisplayName("should throw when subaccount is FAILED")
    void shouldThrowWhenFailed() {
        Subaccount subaccount = TestFixtures.aSubaccount(savedCustomer, SubaccountStatus.FAILED);
        subaccount.setEncryptedApiKey(apiKeyEncryptionService.encrypt("failed_key"));
        subaccountRepository.save(subaccount);

        assertThatThrownBy(() -> asaasApiKeyResolver.resolveForOutbound(savedCustomer.getId()))
                .isInstanceOf(SubaccountOperationBlockedException.class)
                .hasMessageContaining("FAILED");
    }

    @Test
    @DisplayName("should fall back to root key when subaccount has no encrypted API key")
    void shouldFallBackToRootKeyWhenNoEncryptedKey() {
        Subaccount subaccount = TestFixtures.aSubaccount(savedCustomer, SubaccountStatus.ACTIVE);
        subaccount.setEncryptedApiKey(null);
        subaccountRepository.save(subaccount);

        String resolved = asaasApiKeyResolver.resolveForOutbound(savedCustomer.getId());

        assertThat(resolved).isEqualTo(rootApiKey);
    }
}
