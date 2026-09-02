package com.theron.wallet.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.theron.wallet.BaseIntegrationTest;
import com.theron.wallet.TestFixtures;
import com.theron.wallet.dto.asaas.AsaasPixKeyResponse;
import com.theron.wallet.dto.asaas.AsaasTransferResponse;
import com.theron.wallet.dto.asaas.AsaasWebhookPayload;
import com.theron.wallet.dto.request.AddOrganizationMemberRequest;
import com.theron.wallet.dto.request.CreateAccountPixKeyRequest;
import com.theron.wallet.dto.request.CreateAccountRequest;
import com.theron.wallet.dto.request.CreateOrganizationRequest;
import com.theron.wallet.dto.request.CreatePixTransferRequest;
import com.theron.wallet.dto.request.CreateUserRequest;
import com.theron.wallet.dto.response.AccountPixKeyResponse;
import com.theron.wallet.dto.response.AccountResponse;
import com.theron.wallet.dto.response.LedgerBalanceResponse;
import com.theron.wallet.dto.response.OrganizationResponse;
import com.theron.wallet.dto.response.PixTransferResponse;
import com.theron.wallet.dto.response.UserResponse;
import com.theron.wallet.entity.AccountLimit;
import com.theron.wallet.entity.ApprovalPolicy;
import com.theron.wallet.entity.PixTransaction;
import com.theron.wallet.entity.Subaccount;
import com.theron.wallet.entity.Transaction;
import com.theron.wallet.entity.Wallet;
import com.theron.wallet.enums.AccountType;
import com.theron.wallet.enums.ApprovalPolicyStatus;
import com.theron.wallet.enums.DocumentType;
import com.theron.wallet.enums.PixKeyType;
import com.theron.wallet.enums.RoleCode;
import com.theron.wallet.enums.SubaccountStatus;
import com.theron.wallet.enums.TransactionStatus;
import com.theron.wallet.enums.TransactionType;
import com.theron.wallet.repository.AccountLimitRepository;
import com.theron.wallet.repository.AccountRepository;
import com.theron.wallet.repository.ApprovalPolicyRepository;
import com.theron.wallet.repository.PixTransactionRepository;
import com.theron.wallet.repository.SubaccountRepository;
import com.theron.wallet.repository.TransactionRepository;
import com.theron.wallet.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.client.ResourceAccessException;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class PixServiceIntegrationTest extends BaseIntegrationTest {

    private static final String IDEMPOTENCY = "Idempotency-Key";
    private static final String WEBHOOK_TOKEN = "test-webhook-token";

    @Autowired private UserService userService;
    @Autowired private OrganizationService organizationService;
    @Autowired private OrganizationMembershipService membershipService;
    @Autowired private RoleAssignmentService roleAssignmentService;
    @Autowired private AccountService accountService;
    @Autowired private LedgerService ledgerService;
    @Autowired private WebhookService webhookService;
    @Autowired private TransactionLifecycleService transactionLifecycleService;
    @Autowired private SubaccountRepository subaccountRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private AccountLimitRepository accountLimitRepository;
    @Autowired private ApprovalPolicyRepository approvalPolicyRepository;
    @Autowired private WalletRepository walletRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private PixTransactionRepository pixTransactionRepository;
    @Autowired private MockMvc mockMvc;
    @Autowired private PixService pixService;
    @Autowired private WalletService walletService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ObjectMapper objectMapper;

    private OrganizationResponse orgA;
    private OrganizationResponse orgB;
    private UserResponse ownerA;
    private UserResponse employeeA;
    private UserResponse ownerB;
    private AccountResponse accountA;
    private AccountResponse accountB;
    private Subaccount asaasA;
    private String tokenOwnerA;
    private String tokenEmployeeA;
    private String tokenOwnerB;

    @BeforeEach
    void setUpPix() {
        orgA = createOrg("11122233000181");
        orgB = createOrg("11122233000182");
        ownerA = createUser("pix-owner-a@theron.test");
        employeeA = createUser("pix-emp-a@theron.test");
        ownerB = createUser("pix-owner-b@theron.test");

        membershipService.addMember(orgA.getId(), member(ownerA.getId()));
        membershipService.addMember(orgA.getId(), member(employeeA.getId()));
        membershipService.addMember(orgB.getId(), member(ownerB.getId()));
        roleAssignmentService.assignRolesInternal(orgA.getId(), ownerA.getId(), List.of(RoleCode.OWNER.name()));
        roleAssignmentService.assignRolesInternal(orgB.getId(), ownerB.getId(), List.of(RoleCode.OWNER.name()));

        accountA = accountService.create(orgA.getId(), CreateAccountRequest.builder()
                .name("Conta A").type(AccountType.MAIN).build(), ownerA.getId(), "22345678901");
        accountB = accountService.create(orgB.getId(), CreateAccountRequest.builder()
                .name("Conta B").type(AccountType.MAIN).build(), ownerB.getId(), "22345678902");

        asaasA = linkAsaasSubaccount(accountA.getId(), "22345678901", true);
        linkAsaasSubaccount(accountB.getId(), "22345678902", true);

        configureLimits(accountA.getId(), "500.00", "1000.00");
        configureLimits(accountB.getId(), "500.00", "1000.00");
        seedZeroApprovalPolicy(accountA.getId());
        seedZeroApprovalPolicy(accountB.getId());
        fundWallet(accountA.getId(), "1000.00");
        fundWallet(accountB.getId(), "1000.00");

        tokenOwnerA = productAccessToken(ownerA.getEmail());
        tokenEmployeeA = productAccessToken(employeeA.getEmail());
        tokenOwnerB = productAccessToken(ownerB.getEmail());

        when(asaasApiKeyResolver.resolveForSubaccount(any())).thenReturn("encrypted-resolved-key");
        when(asaasTransferClient.retrieveTransfer(any(), any())).thenAnswer(invocation -> {
            String id = invocation.getArgument(1);
            String status = "tr_fail".equals(id) ? "FAILED" : "DONE";
            return AsaasTransferResponse.builder().id(id).status(status).value(new BigDecimal("40.00")).build();
        });
        when(asaasPaymentClient.retrievePayment(any(), any())).thenAnswer(invocation ->
                com.theron.wallet.dto.asaas.AsaasPaymentResponse.builder()
                        .id(invocation.getArgument(1))
                        .status("RECEIVED")
                        .value(new BigDecimal("80.00"))
                        .build());
    }

    @Nested
    @DisplayName("Asaas / Account configuration")
    class AsaasAccountTests {

        @Test
        @DisplayName("1-5,7-8 Account with Asaas configured; API key never in response")
        void configuredAccountHappyPath() throws Exception {
            assertThat(asaasA.getAsaasAccountId()).isNotBlank();
            assertThat(asaasA.getAsaasWalletId()).isNotBlank();
            assertThat(asaasA.getEncryptedApiKey()).isNotNull();

            stubCreatePixKey("pix_1", "evp-key-1");
            MvcResult result = mockMvc.perform(post("/api/v1/pix/keys")
                            .header("Authorization", bearer(tokenOwnerA))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(CreateAccountPixKeyRequest.builder()
                                    .accountId(accountA.getId())
                                    .type(PixKeyType.EVP)
                                    .build())))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.accountId").value(accountA.getId().toString()))
                    .andExpect(jsonPath("$.key").value("evp-key-1"))
                    .andReturn();

            String body = result.getResponse().getContentAsString();
            assertThat(body).doesNotContain("encrypted-resolved-key");
            assertThat(body).doesNotContain("apiKey");
            assertThat(body).doesNotContain("encryptedApiKey");
        }

        @Test
        @DisplayName("2,6 Account without Subaccount or without API key → 422")
        void missingAsaasConfig() throws Exception {
            UserResponse orphanOwner = createUser("pix-orphan@theron.test");
            membershipService.addMember(orgA.getId(), member(orphanOwner.getId()));
            roleAssignmentService.assignRolesInternal(
                    orgA.getId(), orphanOwner.getId(), List.of(RoleCode.EMPLOYEE.name()));
            String tokenOrphan = productAccessToken(orphanOwner.getEmail());

            AccountResponse orphan = accountService.create(orgA.getId(), CreateAccountRequest.builder()
                    .name("Sem Asaas").type(AccountType.RESERVE).build(), orphanOwner.getId(), "22345678911");
            unlinkAsaas(orphan.getId());

            mockMvc.perform(post("/api/v1/pix/keys")
                            .header("Authorization", bearer(tokenOrphan))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(CreateAccountPixKeyRequest.builder()
                                    .accountId(orphan.getId())
                                    .type(PixKeyType.EVP)
                                    .build())))
                    .andExpect(status().isUnprocessableEntity());

            UserResponse noKeyOwner = createUser("pix-nokey@theron.test");
            membershipService.addMember(orgA.getId(), member(noKeyOwner.getId()));
            roleAssignmentService.assignRolesInternal(
                    orgA.getId(), noKeyOwner.getId(), List.of(RoleCode.EMPLOYEE.name()));
            String tokenNoKey = productAccessToken(noKeyOwner.getEmail());

            AccountResponse noKey = accountService.create(orgA.getId(), CreateAccountRequest.builder()
                    .name("Sem key").type(AccountType.EMPLOYEE).build(), noKeyOwner.getId(), "22345678903");
            linkAsaasSubaccount(noKey.getId(), "22345678903", false);

            mockMvc.perform(post("/api/v1/pix/keys")
                            .header("Authorization", bearer(tokenNoKey))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(CreateAccountPixKeyRequest.builder()
                                    .accountId(noKey.getId())
                                    .type(PixKeyType.EVP)
                                    .build())))
                    .andExpect(status().isUnprocessableEntity());
            verify(asaasPixClient, never()).createPixKey(anyString(), any());
        }

        @Test
        @DisplayName("9-10 Cross-tenant blocked; Account A never uses B credential path")
        void crossTenantBlocked() throws Exception {
            stubCreatePixKey("pix_a", "key-a");
            mockMvc.perform(post("/api/v1/pix/keys")
                            .header("Authorization", bearer(tokenOwnerA))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(CreateAccountPixKeyRequest.builder()
                                    .accountId(accountA.getId())
                                    .type(PixKeyType.EVP)
                                    .build())))
                    .andExpect(status().isCreated());

            mockMvc.perform(get("/api/v1/pix/keys")
                            .header("Authorization", bearer(tokenOwnerB))
                            .param("accountId", accountA.getId().toString()))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("PIX keys")
    class PixKeyTests {

        @Test
        @DisplayName("11-13 Create, list, delete")
        void crudKeys() throws Exception {
            stubCreatePixKey("prov_1", "evp-key-crud");
            MvcResult created = mockMvc.perform(post("/api/v1/pix/keys")
                            .header("Authorization", bearer(tokenOwnerA))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(CreateAccountPixKeyRequest.builder()
                                    .accountId(accountA.getId())
                                    .type(PixKeyType.EVP)
                                    .build())))
                    .andExpect(status().isCreated())
                    .andReturn();
            AccountPixKeyResponse key = objectMapper.readValue(
                    created.getResponse().getContentAsString(), AccountPixKeyResponse.class);

            mockMvc.perform(get("/api/v1/pix/keys")
                            .header("Authorization", bearer(tokenOwnerA))
                            .param("accountId", accountA.getId().toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1));

            mockMvc.perform(delete("/api/v1/pix/keys/{id}", key.getId())
                            .header("Authorization", bearer(tokenOwnerA)))
                    .andExpect(status().isNoContent());
            verify(asaasPixClient).deletePixKey(anyString(), org.mockito.ArgumentMatchers.eq("prov_1"));
        }

        @Test
        @DisplayName("14-17 Duplicate, missing account, permission, cross-tenant")
        void keyValidations() throws Exception {
            stubCreatePixKey("prov_dup", "dup-key");
            mockMvc.perform(post("/api/v1/pix/keys")
                            .header("Authorization", bearer(tokenOwnerA))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(CreateAccountPixKeyRequest.builder()
                                    .accountId(accountA.getId())
                                    .type(PixKeyType.EVP)
                                    .build())))
                    .andExpect(status().isCreated());

            when(asaasPixClient.createPixKey(anyString(), any()))
                    .thenReturn(AsaasPixKeyResponse.builder().id("prov_dup2").key("dup-key").type("EVP").status("ACTIVE").build());
            mockMvc.perform(post("/api/v1/pix/keys")
                            .header("Authorization", bearer(tokenOwnerA))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(CreateAccountPixKeyRequest.builder()
                                    .accountId(accountA.getId())
                                    .type(PixKeyType.EVP)
                                    .build())))
                    .andExpect(status().isConflict());

            mockMvc.perform(post("/api/v1/pix/keys")
                            .header("Authorization", bearer(tokenOwnerA))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(CreateAccountPixKeyRequest.builder()
                                    .accountId(UUID.randomUUID())
                                    .type(PixKeyType.EVP)
                                    .build())))
                    .andExpect(status().isNotFound());

            mockMvc.perform(post("/api/v1/pix/keys")
                            .header("Authorization", bearer(tokenEmployeeA))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(CreateAccountPixKeyRequest.builder()
                                    .accountId(accountA.getId())
                                    .type(PixKeyType.EVP)
                                    .build())))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Create with CPF is rejected before Asaas")
        void nonEvpTypeRejected() throws Exception {
            reset(asaasPixClient);
            mockMvc.perform(post("/api/v1/pix/keys")
                            .header("Authorization", bearer(tokenOwnerA))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(CreateAccountPixKeyRequest.builder()
                                    .accountId(accountA.getId())
                                    .type(PixKeyType.CPF)
                                    .build())))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                    .andExpect(jsonPath("$.message").value(PixKeyType.PROVIDER_CREATE_UNSUPPORTED_MESSAGE));
            verify(asaasPixClient, never()).createPixKey(anyString(), any());
        }
    }

    @Nested
    @DisplayName("Transfers")
    class TransferTests {

        @Test
        @DisplayName("18,35 Transfer válida + Asaas success")
        void transferHappyPath() throws Exception {
            stubTransfer("tr_ok");
            mockMvc.perform(post("/api/v1/pix/transfers")
                            .header("Authorization", bearer(tokenOwnerA))
                            .header(IDEMPOTENCY, UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(transferBody(accountA.getId(), "50.00"))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("PROCESSING"))
                    .andExpect(jsonPath("$.providerReference").value("tr_ok"));
        }

        @Test
        @DisplayName("19 Saldo insuficiente")
        void insufficientBalance() throws Exception {
            fundWallet(accountA.getId(), "10.00");
            mockMvc.perform(post("/api/v1/pix/transfers")
                            .header("Authorization", bearer(tokenOwnerA))
                            .header(IDEMPOTENCY, UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(transferBody(accountA.getId(), "50.00"))))
                    .andExpect(status().isConflict());
            verify(asaasTransferClient, never()).createTransfer(anyString(), any());
        }

        @Test
        @DisplayName("20,23 Valor acima do limite da operação")
        void aboveOperationLimit() throws Exception {
            configureLimits(accountA.getId(), "100.00", "1000.00");
            mockMvc.perform(post("/api/v1/pix/transfers")
                            .header("Authorization", bearer(tokenOwnerA))
                            .header(IDEMPOTENCY, UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(transferBody(accountA.getId(), "100.01"))))
                    .andExpect(status().isUnprocessableEntity());
            verify(asaasTransferClient, never()).createTransfer(anyString(), any());
        }

        @Test
        @DisplayName("22 Valor exatamente no limite da operação")
        void exactlyOperationLimit() throws Exception {
            configureLimits(accountA.getId(), "100.00", "1000.00");
            stubTransfer("tr_exact");
            mockMvc.perform(post("/api/v1/pix/transfers")
                            .header("Authorization", bearer(tokenOwnerA))
                            .header(IDEMPOTENCY, UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(transferBody(accountA.getId(), "100.00"))))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("21,24-25 Soma diária no limite e acima")
        void dailyLimits() throws Exception {
            configureLimits(accountA.getId(), "200.00", "100.00");
            stubTransfer("tr_d1");
            mockMvc.perform(post("/api/v1/pix/transfers")
                            .header("Authorization", bearer(tokenOwnerA))
                            .header(IDEMPOTENCY, UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(transferBody(accountA.getId(), "100.00"))))
                    .andExpect(status().isCreated());

            reset(asaasTransferClient);
            mockMvc.perform(post("/api/v1/pix/transfers")
                            .header("Authorization", bearer(tokenOwnerA))
                            .header(IDEMPOTENCY, UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(transferBody(accountA.getId(), "0.01"))))
                    .andExpect(status().isUnprocessableEntity());
            verify(asaasTransferClient, never()).createTransfer(anyString(), any());
        }

        @Test
        @DisplayName("26 Account sem limite configurado")
        void noLimitConfigured() throws Exception {
            UserResponse noLimitOwner = createUser("pix-nolimit@theron.test");
            membershipService.addMember(orgA.getId(), member(noLimitOwner.getId()));
            roleAssignmentService.assignRolesInternal(
                    orgA.getId(), noLimitOwner.getId(), List.of(RoleCode.OWNER.name()));
            String tokenNoLimit = productAccessToken(noLimitOwner.getEmail());

            AccountResponse noLimit = accountService.create(orgA.getId(), CreateAccountRequest.builder()
                    .name("No limit").type(AccountType.RESERVE).build(), noLimitOwner.getId(), "22345678904");
            linkAsaasSubaccount(noLimit.getId(), "22345678904", true);
            fundWallet(noLimit.getId(), "100.00");

            mockMvc.perform(post("/api/v1/pix/transfers")
                            .header("Authorization", bearer(tokenNoLimit))
                            .header(IDEMPOTENCY, UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(transferBody(noLimit.getId(), "10.00"))))
                    .andExpect(status().isUnprocessableEntity());
            verify(asaasTransferClient, never()).createTransfer(anyString(), any());
        }

        @Test
        @DisplayName("27 Bypass accountId cross-tenant")
        void bypassAccountId() throws Exception {
            mockMvc.perform(post("/api/v1/pix/transfers")
                            .header("Authorization", bearer(tokenOwnerB))
                            .header(IDEMPOTENCY, UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(transferBody(accountA.getId(), "10.00"))))
                    .andExpect(status().isForbidden());
            verify(asaasTransferClient, never()).createTransfer(anyString(), any());
        }

        @Test
        @DisplayName("29-31 Idempotency")
        void idempotency() throws Exception {
            stubTransfer("tr_idem");
            String key = "idem-pix-1";
            CreatePixTransferRequest body = transferBody(accountA.getId(), "25.00");

            MvcResult first = mockMvc.perform(post("/api/v1/pix/transfers")
                            .header("Authorization", bearer(tokenOwnerA))
                            .header(IDEMPOTENCY, key)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isCreated())
                    .andReturn();
            PixTransferResponse r1 = objectMapper.readValue(first.getResponse().getContentAsString(), PixTransferResponse.class);

            MvcResult second = mockMvc.perform(post("/api/v1/pix/transfers")
                            .header("Authorization", bearer(tokenOwnerA))
                            .header(IDEMPOTENCY, key)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isCreated())
                    .andReturn();
            PixTransferResponse r2 = objectMapper.readValue(second.getResponse().getContentAsString(), PixTransferResponse.class);
            assertThat(r2.getId()).isEqualTo(r1.getId());
            verify(asaasTransferClient, times(1)).createTransfer(anyString(), any());

            CreatePixTransferRequest different = transferBody(accountA.getId(), "26.00");
            mockMvc.perform(post("/api/v1/pix/transfers")
                            .header("Authorization", bearer(tokenOwnerA))
                            .header(IDEMPOTENCY, key)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(different)))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("32 Concorrência no limite diário")
        void concurrentDailyLimit() throws Exception {
            configureLimits(accountA.getId(), "200.00", "100.00");
            stubTransfer("tr_conc");

            ExecutorService pool = Executors.newFixedThreadPool(2);
            AtomicInteger created = new AtomicInteger();
            AtomicInteger rejected = new AtomicInteger();
            Callable<Void> task = () -> {
                try {
                    mockMvc.perform(post("/api/v1/pix/transfers")
                                    .header("Authorization", bearer(tokenOwnerA))
                                    .header(IDEMPOTENCY, UUID.randomUUID().toString())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(transferBody(accountA.getId(), "60.00"))))
                            .andExpect(result -> {
                                int s = result.getResponse().getStatus();
                                if (s == 201) {
                                    created.incrementAndGet();
                                } else if (s == 422) {
                                    rejected.incrementAndGet();
                                } else {
                                    throw new AssertionError("Unexpected status " + s);
                                }
                            });
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                return null;
            };
            Future<Void> f1 = pool.submit(task);
            Future<Void> f2 = pool.submit(task);
            f1.get();
            f2.get();
            pool.shutdown();
            assertThat(created.get() + rejected.get()).isEqualTo(2);
            assertThat(created.get()).isLessThanOrEqualTo(1);
            assertThat(rejected.get()).isGreaterThanOrEqualTo(1);
        }

        @Test
        @DisplayName("33-34,36 Timeout, failure, retry")
        void timeoutFailureRetry() {
            when(asaasTransferClient.createTransfer(anyString(), any()))
                    .thenThrow(new ResourceAccessException("timeout"));

            String key = "idem-timeout";
            CreatePixTransferRequest body = transferBody(accountA.getId(), "15.00");
            body.setIdempotencyKey(key);

            assertThatThrownBy(() -> pixService.createTransfer(ownerA.getId(), body))
                    .isInstanceOf(ResourceAccessException.class);

            List<Transaction> afterTimeout = transactionRepository.findAll().stream()
                    .filter(t -> key.equals(t.getIdempotencyKey()))
                    .toList();
            assertThat(afterTimeout).hasSize(1);
            assertThat(afterTimeout.getFirst().getStatus()).isEqualTo(TransactionStatus.PROCESSING);
            assertThat(afterTimeout.getFirst().getAsaasPaymentId()).isNull();

            reset(asaasTransferClient);
            stubTransfer("tr_retry");
            PixTransferResponse retried = pixService.createTransfer(ownerA.getId(), body);
            assertThat(retried.getProviderReference()).isEqualTo("tr_retry");
            assertThat(retried.getStatus()).isEqualTo(TransactionStatus.PROCESSING);

            reset(asaasTransferClient);
            when(asaasTransferClient.createTransfer(anyString(), any()))
                    .thenThrow(new RuntimeException("asaas down"));
            CreatePixTransferRequest failBody = transferBody(accountA.getId(), "12.00");
            failBody.setIdempotencyKey(UUID.randomUUID().toString());
            assertThatThrownBy(() -> pixService.createTransfer(ownerA.getId(), failBody))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("asaas down");
            assertThat(transactionRepository.findAll().stream()
                    .anyMatch(t -> t.getStatus() == TransactionStatus.FAILED)).isTrue();
        }

        @Test
        @DisplayName("EMPLOYEE cannot transfer from OWNER account (own-account only)")
        void employeeCannotTransferFromOwnerAccount() throws Exception {
            mockMvc.perform(post("/api/v1/pix/transfers")
                            .header("Authorization", bearer(tokenEmployeeA))
                            .header(IDEMPOTENCY, UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(transferBody(accountA.getId(), "10.00"))))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("EMPLOYEE can transfer on own account → PROCESSING")
        void employeeCanTransferOwnAccount() throws Exception {
            AccountResponse empAccount = accountService.create(orgA.getId(), CreateAccountRequest.builder()
                    .name("Emp Wallet").type(AccountType.EMPLOYEE).build(), employeeA.getId(), "22345678905");
            linkAsaasSubaccount(empAccount.getId(), "22345678905", true);
            configureLimits(empAccount.getId(), "500.00", "1000.00");
            seedZeroApprovalPolicy(empAccount.getId());
            fundWallet(empAccount.getId(), "200.00");
            stubTransfer("tr_emp_own");

            mockMvc.perform(post("/api/v1/pix/transfers")
                            .header("Authorization", bearer(tokenEmployeeA))
                            .header(IDEMPOTENCY, UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(transferBody(empAccount.getId(), "10.00"))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("PROCESSING"));
        }
    }

    @Nested
    @DisplayName("Webhooks and ledger")
    class WebhookLedgerTests {

        @Test
        @DisplayName("37-42 Webhook válido/inválido/duplicado; ledger sem duplicar")
        void webhooksAndLedger() throws Exception {
            stubTransfer("tr_wh");
            MvcResult created = mockMvc.perform(post("/api/v1/pix/transfers")
                            .header("Authorization", bearer(tokenOwnerA))
                            .header(IDEMPOTENCY, UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(transferBody(accountA.getId(), "40.00"))))
                    .andExpect(status().isCreated())
                    .andReturn();
            PixTransferResponse transfer = objectMapper.readValue(
                    created.getResponse().getContentAsString(), PixTransferResponse.class);

            BigDecimal walletBefore = walletRepository.findByAccount_Id(accountA.getId()).orElseThrow().getBalance();

            mockMvc.perform(post("/api/v1/webhooks/asaas")
                            .header("asaas-access-token", "wrong")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(transferWebhook("TRANSFER_DONE", "tr_wh"))))
                    .andExpect(status().isUnauthorized());

            mockMvc.perform(post("/api/v1/webhooks/asaas")
                            .header("asaas-access-token", WEBHOOK_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(transferWebhook("TRANSFER_DONE", "tr_wh"))))
                    .andExpect(status().isOk());

            Transaction tx = transactionRepository.findById(transfer.getTransactionId()).orElseThrow();
            assertThat(tx.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
            PixTransaction pixTx = pixTransactionRepository.findByTransactionId(tx.getId()).orElseThrow();
            assertThat(pixTx.getStatus()).isEqualTo(TransactionStatus.COMPLETED);

            // duplicate webhook
            mockMvc.perform(post("/api/v1/webhooks/asaas")
                            .header("asaas-access-token", WEBHOOK_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(transferWebhook("TRANSFER_DONE", "tr_wh"))))
                    .andExpect(status().isOk());
            assertThat(walletRepository.findByAccount_Id(accountA.getId()).orElseThrow().getBalance())
                    .isEqualByComparingTo(walletBefore);

            LedgerBalanceResponse ledger = ledgerService.getBalance(accountA.getId());
            assertThat(ledger.getBalance()).isEqualByComparingTo(walletBefore);
        }

        @Test
        @DisplayName("13 Failure webhook credits back")
        void failureWebhook() throws Exception {
            stubTransfer("tr_fail");
            BigDecimal before = walletRepository.findByAccount_Id(accountA.getId()).orElseThrow().getBalance();
            mockMvc.perform(post("/api/v1/pix/transfers")
                            .header("Authorization", bearer(tokenOwnerA))
                            .header(IDEMPOTENCY, UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(transferBody(accountA.getId(), "30.00"))))
                    .andExpect(status().isCreated());

            mockMvc.perform(post("/api/v1/webhooks/asaas")
                            .header("asaas-access-token", WEBHOOK_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(transferWebhook("TRANSFER_FAILED", "tr_fail"))))
                    .andExpect(status().isOk());

            assertThat(walletRepository.findByAccount_Id(accountA.getId()).orElseThrow().getBalance())
                    .isEqualByComparingTo(before);
            assertThat(transactionRepository.findAll().getFirst().getStatus()).isEqualTo(TransactionStatus.FAILED);
        }

        @Test
        @DisplayName("43,49-50 REVERSED used; REFUNDED not created")
        void reversedNotRefunded() throws Exception {
            stubTransfer("tr_rev");
            MvcResult created = mockMvc.perform(post("/api/v1/pix/transfers")
                            .header("Authorization", bearer(tokenOwnerA))
                            .header(IDEMPOTENCY, UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(transferBody(accountA.getId(), "20.00"))))
                    .andExpect(status().isCreated())
                    .andReturn();
            PixTransferResponse transfer = objectMapper.readValue(
                    created.getResponse().getContentAsString(), PixTransferResponse.class);

            webhookService.processTransferWebhook(transferWebhook("TRANSFER_DONE", "tr_rev"));
            Transaction tx = transactionRepository.findById(transfer.getTransactionId()).orElseThrow();
            transactionLifecycleService.transition(tx, TransactionStatus.REVERSED);
            PixTransaction pixTx = pixTransactionRepository.findByTransactionId(tx.getId()).orElseThrow();
            pixTx.setStatus(TransactionStatus.REVERSED);
            pixTransactionRepository.save(pixTx);

            assertThat(tx.getStatus()).isEqualTo(TransactionStatus.REVERSED);
            assertThat(Arrays.stream(TransactionStatus.values()).map(Enum::name))
                    .doesNotContain("REFUNDED");
            assertThat(pixTx.getStatus().name()).isNotEqualTo("REFUNDED");
        }

        @Test
        @DisplayName("15-16 Ledger and reconciliation after COMPLETED")
        void ledgerReconciliation() throws Exception {
            stubTransfer("tr_led");
            BigDecimal before = walletRepository.findByAccount_Id(accountA.getId()).orElseThrow().getBalance();
            mockMvc.perform(post("/api/v1/pix/transfers")
                            .header("Authorization", bearer(tokenOwnerA))
                            .header(IDEMPOTENCY, UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(transferBody(accountA.getId(), "55.00"))))
                    .andExpect(status().isCreated());
            webhookService.processTransferWebhook(transferWebhook("TRANSFER_DONE", "tr_led"));

            BigDecimal walletAfter = walletRepository.findByAccount_Id(accountA.getId()).orElseThrow().getBalance();
            assertThat(walletAfter).isEqualByComparingTo(before.subtract(new BigDecimal("55.00")));
            assertThat(ledgerService.getBalance(accountA.getId()).getBalance())
                    .isEqualByComparingTo(walletAfter);
        }
    }

    @Nested
    @DisplayName("Status enum")
    class StatusTests {

        @Test
        @DisplayName("44-50 Lifecycle statuses present; no REFUNDED")
        void statusSet() {
            assertThat(TransactionStatus.values()).contains(
                    TransactionStatus.PENDING,
                    TransactionStatus.PENDING_APPROVAL,
                    TransactionStatus.PROCESSING,
                    TransactionStatus.COMPLETED,
                    TransactionStatus.FAILED,
                    TransactionStatus.CANCELLED,
                    TransactionStatus.REVERSED);
            assertThat(TransactionStatus.values()).hasSize(7);
            assertThat(Arrays.stream(TransactionStatus.values()).map(Enum::name))
                    .doesNotContain("REFUNDED");
            assertThat(TransactionType.PIX).isNotNull();
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void stubCreatePixKey(String providerId, String key) {
        when(asaasPixClient.createPixKey(anyString(), any()))
                .thenReturn(AsaasPixKeyResponse.builder()
                        .id(providerId)
                        .key(key)
                        .type("EVP")
                        .status("ACTIVE")
                        .build());
    }

    private void stubTransfer(String transferId) {
        when(asaasTransferClient.createTransfer(anyString(), any()))
                .thenReturn(AsaasTransferResponse.builder()
                        .id(transferId)
                        .value(new BigDecimal("50.00"))
                        .status("PENDING")
                        .operationType("PIX")
                        .build());
    }

    private static CreatePixTransferRequest transferBody(UUID accountId, String amount) {
        return CreatePixTransferRequest.builder()
                .accountId(accountId)
                .amount(new BigDecimal(amount))
                .destinationPixKey("12345678901")
                .destinationPixKeyType(PixKeyType.CPF)
                .description("PIX test")
                .build();
    }

    private static AsaasWebhookPayload transferWebhook(String event, String transferId) {
        return AsaasWebhookPayload.builder()
                .event(event)
                .transfer(AsaasWebhookPayload.Transfer.builder().id(transferId).build())
                .build();
    }

    private void unlinkAsaas(UUID accountId) {
        subaccountRepository.findByAccount_Id(accountId).ifPresent(sub -> {
            sub.setEncryptedApiKey(null);
            sub.setAsaasAccountId(null);
            sub.setAsaasWalletId(null);
            sub.transitionTo(SubaccountStatus.FAILED, "unlinked for test");
            subaccountRepository.saveAndFlush(sub);
        });
    }

    private Subaccount linkAsaasSubaccount(UUID accountId, String cpf, boolean withApiKey) {
        Subaccount sub = subaccountRepository.findByAccount_Id(accountId).orElse(null);
        if (sub == null) {
            sub = TestFixtures.aSubaccount(cpf, SubaccountStatus.ACTIVE);
            sub = subaccountRepository.saveAndFlush(sub);
            jdbcTemplate.update("UPDATE subaccount SET account_id = ? WHERE id = ?", accountId, sub.getId());
            sub = subaccountRepository.findById(sub.getId()).orElseThrow();
        }
        sub.setCpfCnpj(cpf);
        sub.setAsaasAccountId("asaas_acc_" + cpf);
        sub.setAsaasWalletId("asaas_wal_" + cpf);
        sub.setStatus(SubaccountStatus.ACTIVE);
        sub.setLegacyAutoProvisioned(true);
        sub.setEncryptedApiKey(withApiKey ? new byte[]{1, 2, 3, 4, 5, 6, 7, 8} : null);
        if (!withApiKey) {
            sub.transitionTo(SubaccountStatus.FAILED, "no api key");
        }
        return subaccountRepository.saveAndFlush(sub);
    }

    private void configureLimits(UUID accountId, String maxOp, String daily) {
        jdbcTemplate.update("DELETE FROM account_limit WHERE account_id = ?", accountId);
        accountLimitRepository.saveAndFlush(AccountLimit.builder()
                .accountId(accountId)
                .maxOperationAmount(new BigDecimal(maxOp))
                .dailyLimitAmount(new BigDecimal(daily))
                .build());
    }

    private void seedZeroApprovalPolicy(UUID accountId) {
        var account = accountRepository.findByIdWithOrganization(accountId).orElseThrow();
        approvalPolicyRepository.saveAndFlush(ApprovalPolicy.builder()
                .account(account)
                .organization(account.getOrganization())
                .amountMin(BigDecimal.ZERO)
                .amountMax(null)
                .requiredApprovals(0)
                .status(ApprovalPolicyStatus.ACTIVE)
                .build());
    }

    private void fundWallet(UUID accountId, String balance) {
        Wallet wallet = walletRepository.findByAccount_Id(accountId).orElseThrow();
        BigDecimal target = new BigDecimal(balance);
        BigDecimal current = wallet.getBalance();
        BigDecimal delta = target.subtract(current);
        if (delta.compareTo(BigDecimal.ZERO) > 0) {
            walletService.credit(wallet.getId(), delta);
        } else if (delta.compareTo(BigDecimal.ZERO) < 0) {
            wallet.setBalance(target);
            walletRepository.save(wallet);
        }
    }

    private OrganizationResponse createOrg(String document) {
        return organizationService.create(CreateOrganizationRequest.builder()
                .legalName("Org " + document)
                .document(document)
                .documentType(DocumentType.CNPJ)
                .build());
    }

    private UserResponse createUser(String email) {
        return userService.create(CreateUserRequest.builder()
                .name(email)
                .email(email)
                .password("SenhaForte1!")
                .build());
    }

    private static AddOrganizationMemberRequest member(UUID userId) {
        return AddOrganizationMemberRequest.builder().userId(userId).build();
    }
}
