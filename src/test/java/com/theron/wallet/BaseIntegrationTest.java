package com.theron.wallet;

import com.theron.wallet.integration.AsaasCustomerClient;
import com.theron.wallet.integration.AsaasPaymentClient;
import com.theron.wallet.integration.AsaasSubaccountClient;
import com.theron.wallet.integration.AsaasTransferClient;
import com.theron.wallet.integration.AsaasWebhookClient;
import com.theron.wallet.repository.SubaccountApiKeyAuditRepository;
import com.theron.wallet.repository.SubaccountRepository;
import com.theron.wallet.repository.TransactionRepository;
import com.theron.wallet.repository.WalletRepository;
import com.theron.wallet.repository.CustomerRepository;
import com.theron.wallet.security.AsaasApiKeyResolver;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

    @MockitoBean
    protected AsaasCustomerClient asaasCustomerClient;

    @MockitoBean
    protected AsaasPaymentClient asaasPaymentClient;

    @MockitoBean
    protected AsaasSubaccountClient asaasSubaccountClient;

    @MockitoBean
    protected AsaasTransferClient asaasTransferClient;

    @MockitoBean
    protected AsaasWebhookClient asaasWebhookClient;

    @MockitoBean
    protected AsaasApiKeyResolver asaasApiKeyResolver;

    @Autowired
    private SubaccountApiKeyAuditRepository baseAuditRepository;

    @Autowired
    private SubaccountRepository baseSubaccountRepository;

    @Autowired
    private TransactionRepository baseTransactionRepository;

    @Autowired
    private WalletRepository baseWalletRepository;

    @Autowired
    private CustomerRepository baseCustomerRepository;

    /**
     * Central cleanup respecting FK order:
     * audit → subaccount → transaction → wallet → customer
     */
    @BeforeEach
    void cleanDatabase() {
        baseAuditRepository.deleteAll();
        baseSubaccountRepository.deleteAll();
        baseTransactionRepository.deleteAll();
        baseWalletRepository.deleteAll();
        baseCustomerRepository.deleteAll();
    }
}
