package com.theron.wallet;
import com.theron.wallet.integration.AsaasCustomerClient;
import com.theron.wallet.integration.AsaasPaymentClient;
import com.theron.wallet.integration.AsaasPixClient;
import com.theron.wallet.integration.AsaasSubaccountClient;
import com.theron.wallet.integration.AsaasTransferClient;
import com.theron.wallet.integration.AsaasWebhookClient;
import com.theron.wallet.repository.AccountLimitRepository;
import com.theron.wallet.repository.AccountRepository;
import com.theron.wallet.repository.ApprovalActionRepository;
import com.theron.wallet.repository.ApprovalPolicyRepository;
import com.theron.wallet.repository.ApprovalRequestRepository;
import com.theron.wallet.repository.AuthSessionRepository;
import com.theron.wallet.repository.BeneficiaryRepository;
import com.theron.wallet.repository.CustomerRepository;
import com.theron.wallet.repository.DeviceRepository;
import com.theron.wallet.repository.LedgerAccountRepository;
import com.theron.wallet.repository.LedgerEntryRepository;
import com.theron.wallet.repository.LedgerTransactionRepository;
import com.theron.wallet.repository.MembershipRoleRepository;
import com.theron.wallet.repository.OrganizationMembershipRepository;
import com.theron.wallet.repository.OrganizationRepository;
import com.theron.wallet.repository.PixKeyRepository;
import com.theron.wallet.repository.PixTransactionRepository;
import com.theron.wallet.repository.SubaccountApiKeyAuditRepository;
import com.theron.wallet.repository.SubaccountRepository;
import com.theron.wallet.repository.TransactionRepository;
import com.theron.wallet.repository.UserRepository;
import com.theron.wallet.repository.WalletRepository;
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
    protected AsaasPaymentClient asaasPaymentClient;
    @MockitoBean
    protected AsaasSubaccountClient asaasSubaccountClient;
    @MockitoBean
    protected AsaasTransferClient asaasTransferClient;
    @MockitoBean
    protected AsaasWebhookClient asaasWebhookClient;
    @MockitoBean
    protected AsaasCustomerClient asaasCustomerClient;
    @MockitoBean
    protected AsaasPixClient asaasPixClient;
    @MockitoBean
    protected AsaasApiKeyResolver asaasApiKeyResolver;
    @Autowired
    private SubaccountApiKeyAuditRepository baseAuditRepository;
    @Autowired
    private SubaccountRepository baseSubaccountRepository;
    @Autowired
    private TransactionRepository baseTransactionRepository;
    @Autowired
    private PixTransactionRepository basePixTransactionRepository;
    @Autowired
    private ApprovalActionRepository baseApprovalActionRepository;
    @Autowired
    private ApprovalRequestRepository baseApprovalRequestRepository;
    @Autowired
    private ApprovalPolicyRepository baseApprovalPolicyRepository;
    @Autowired
    private PixKeyRepository basePixKeyRepository;
    @Autowired
    private AccountLimitRepository baseAccountLimitRepository;
    @Autowired
    private BeneficiaryRepository baseBeneficiaryRepository;
    @Autowired
    private WalletRepository baseWalletRepository;
    @Autowired
    private LedgerEntryRepository baseLedgerEntryRepository;
    @Autowired
    private LedgerTransactionRepository baseLedgerTransactionRepository;
    @Autowired
    private LedgerAccountRepository baseLedgerAccountRepository;
    @Autowired
    private AccountRepository baseAccountRepository;
    @Autowired
    private CustomerRepository baseCustomerRepository;
    @Autowired
    private AuthSessionRepository baseAuthSessionRepository;
    @Autowired
    private DeviceRepository baseDeviceRepository;
    @Autowired
    private MembershipRoleRepository baseMembershipRoleRepository;
    @Autowired
    private OrganizationMembershipRepository baseMembershipRepository;
    @Autowired
    private UserRepository baseUserRepository;
    @Autowired
    private OrganizationRepository baseOrganizationRepository;

    /**
     * Central cleanup respecting FK order:
     * audit → ledger → approval_action → approval_request → pix_transaction → transaction →
     * approval_policy → pix_key → account_limit → beneficiary → wallet → subaccount →
     * account → customer → auth_session → device → membership_role → membership →
     * app_user → organization
     */
    @BeforeEach
    void cleanDatabase() {
        baseAuditRepository.deleteAll();
        baseLedgerEntryRepository.deleteAll();
        baseLedgerTransactionRepository.deleteAll();
        baseLedgerAccountRepository.deleteAll();
        baseApprovalActionRepository.deleteAll();
        baseApprovalRequestRepository.deleteAll();
        basePixTransactionRepository.deleteAll();
        baseTransactionRepository.deleteAll();
        baseApprovalPolicyRepository.deleteAll();
        basePixKeyRepository.deleteAll();
        baseAccountLimitRepository.deleteAll();
        baseBeneficiaryRepository.deleteAll();
        baseWalletRepository.deleteAll();
        baseSubaccountRepository.deleteAll();
        baseAccountRepository.deleteAll();
        baseCustomerRepository.deleteAll();
        baseAuthSessionRepository.deleteAll();
        baseDeviceRepository.deleteAll();
        baseMembershipRoleRepository.deleteAll();
        baseMembershipRepository.deleteAll();
        baseUserRepository.deleteAll();
        baseOrganizationRepository.deleteAll();
    }
}
