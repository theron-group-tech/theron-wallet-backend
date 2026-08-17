package com.theron.wallet.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.theron.wallet.BaseIntegrationTest;
import com.theron.wallet.TestFixtures;
import com.theron.wallet.dto.asaas.AsaasTransferResponse;
import com.theron.wallet.dto.request.AddOrganizationMemberRequest;
import com.theron.wallet.dto.request.CreateAccountRequest;
import com.theron.wallet.dto.request.CreateOrganizationRequest;
import com.theron.wallet.dto.request.CreatePixTransferRequest;
import com.theron.wallet.dto.request.CreateTransactionLimitRequest;
import com.theron.wallet.dto.request.CreateUserRequest;
import com.theron.wallet.dto.request.UpdateTransactionLimitRequest;
import com.theron.wallet.dto.response.AccountResponse;
import com.theron.wallet.dto.response.OrganizationResponse;
import com.theron.wallet.dto.response.TransactionLimitResponse;
import com.theron.wallet.dto.response.UserResponse;
import com.theron.wallet.entity.AccountLimit;
import com.theron.wallet.entity.ApprovalPolicy;
import com.theron.wallet.entity.Subaccount;
import com.theron.wallet.entity.Wallet;
import com.theron.wallet.enums.AccountType;
import com.theron.wallet.enums.ApprovalPolicyStatus;
import com.theron.wallet.enums.DocumentType;
import com.theron.wallet.enums.LimitPeriod;
import com.theron.wallet.enums.LimitTransactionType;
import com.theron.wallet.enums.PixKeyType;
import com.theron.wallet.enums.RoleCode;
import com.theron.wallet.enums.SubaccountStatus;
import com.theron.wallet.repository.AccountLimitRepository;
import com.theron.wallet.repository.AccountRepository;
import com.theron.wallet.repository.ApprovalPolicyRepository;
import com.theron.wallet.repository.RoleRepository;
import com.theron.wallet.repository.SubaccountRepository;
import com.theron.wallet.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class TransactionLimitIntegrationTest extends BaseIntegrationTest {

    private static final String ACTOR = "X-Actor-User-Id";
    private static final String IDEMPOTENCY = "Idempotency-Key";

    @Autowired private UserService userService;
    @Autowired private OrganizationService organizationService;
    @Autowired private OrganizationMembershipService membershipService;
    @Autowired private RoleAssignmentService roleAssignmentService;
    @Autowired private AccountService accountService;
    @Autowired private WalletService walletService;
    @Autowired private SubaccountRepository subaccountRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private AccountLimitRepository accountLimitRepository;
    @Autowired private ApprovalPolicyRepository approvalPolicyRepository;
    @Autowired private WalletRepository walletRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    private OrganizationResponse org;
    private UserResponse owner;
    private UserResponse finance;
    private UserResponse employee;
    private AccountResponse accountA;
    private AccountResponse accountB;
    private UUID financeRoleId;

    @BeforeEach
    void setUp() {
        org = organizationService.create(CreateOrganizationRequest.builder()
                .legalName("Limits Org")
                .document("55667788000111")
                .documentType(DocumentType.CNPJ)
                .build());
        owner = createUser("lim-owner@theron.test");
        finance = createUser("lim-finance@theron.test");
        employee = createUser("lim-emp@theron.test");

        membershipService.addMember(org.getId(), member(owner.getId()));
        membershipService.addMember(org.getId(), member(finance.getId()));
        membershipService.addMember(org.getId(), member(employee.getId()));
        roleAssignmentService.assignRolesInternal(org.getId(), owner.getId(), List.of(RoleCode.OWNER.name()));
        roleAssignmentService.assignRolesInternal(org.getId(), finance.getId(), List.of(RoleCode.FINANCE.name()));
        roleAssignmentService.assignRolesInternal(org.getId(), employee.getId(), List.of(RoleCode.EMPLOYEE.name()));

        financeRoleId = roleRepository.findByCode(RoleCode.FINANCE.name()).orElseThrow().getId();

        accountA = accountService.create(org.getId(), CreateAccountRequest.builder()
                .name("Conta A").type(AccountType.MAIN).build());
        accountB = accountService.create(org.getId(), CreateAccountRequest.builder()
                .name("Conta B").type(AccountType.EMPLOYEE).build());

        linkAsaas(accountA.getId(), "55667788901");
        linkAsaas(accountB.getId(), "55667788902");
        configureAccountLimits(accountA.getId(), "500.00", "1000.00");
        configureAccountLimits(accountB.getId(), "500.00", "1000.00");
        seedZeroApprovalPolicy(accountA.getId());
        seedZeroApprovalPolicy(accountB.getId());
        fundWallet(accountA.getId(), "1000.00");
        fundWallet(accountB.getId(), "1000.00");

        when(asaasApiKeyResolver.resolveForSubaccount(any())).thenReturn("encrypted-resolved-key");
    }

    @Test
    @DisplayName("1. Dentro do limite PER_TRANSACTION — PIX 201")
    void withinPerTransactionLimit() throws Exception {
        createLimit(orgWide(LimitPeriod.PER_TRANSACTION, "100.00"));
        stubTransfer("tr_lim_ok");

        pix(owner.getId(), accountA.getId(), "80.00")
                .andExpect(status().isCreated());
        assertBalance(accountA.getId(), "920.00");
    }

    @Test
    @DisplayName("2. Acima do PER_TRANSACTION — 422, saldo intacto, Asaas never")
    void abovePerTransactionLimit() throws Exception {
        createLimit(orgWide(LimitPeriod.PER_TRANSACTION, "100.00"));

        pix(owner.getId(), accountA.getId(), "100.01")
                .andExpect(status().isUnprocessableEntity());

        assertBalance(accountA.getId(), "1000.00");
        verify(asaasTransferClient, never()).createTransfer(anyString(), any());
    }

    @Test
    @DisplayName("3. Diário — 1ª operação OK; 2ª estoura DAILY → 422")
    void dailyLimitSecondOperationRejected() throws Exception {
        createLimit(orgWide(LimitPeriod.DAILY, "100.00"));
        stubTransfer("tr_lim_day");

        pix(owner.getId(), accountA.getId(), "80.00")
                .andExpect(status().isCreated());

        reset(asaasTransferClient);
        pix(owner.getId(), accountA.getId(), "30.00")
                .andExpect(status().isUnprocessableEntity());

        assertBalance(accountA.getId(), "920.00");
        verify(asaasTransferClient, never()).createTransfer(anyString(), any());
    }

    @Test
    @DisplayName("4. Mensal — 1ª operação OK; 2ª estoura MONTHLY → 422")
    void monthlyLimitSecondOperationRejected() throws Exception {
        createLimit(orgWide(LimitPeriod.MONTHLY, "100.00"));
        stubTransfer("tr_lim_month");

        pix(owner.getId(), accountA.getId(), "80.00")
                .andExpect(status().isCreated());

        reset(asaasTransferClient);
        pix(owner.getId(), accountA.getId(), "30.00")
                .andExpect(status().isUnprocessableEntity());

        assertBalance(accountA.getId(), "920.00");
        verify(asaasTransferClient, never()).createTransfer(anyString(), any());
    }

    @Test
    @DisplayName("5. Por user — A limitado 422; B (OWNER) na mesma account passa")
    void perUserLimitAppliesOnlyToThatUser() throws Exception {
        createLimit(CreateTransactionLimitRequest.builder()
                .organizationId(org.getId())
                .userId(finance.getId())
                .transactionType(LimitTransactionType.PIX)
                .period(LimitPeriod.PER_TRANSACTION)
                .maxAmount(new BigDecimal("50.00"))
                .build());
        stubTransfer("tr_lim_user");

        pix(finance.getId(), accountA.getId(), "80.00")
                .andExpect(status().isUnprocessableEntity());
        assertBalance(accountA.getId(), "1000.00");

        pix(owner.getId(), accountA.getId(), "80.00")
                .andExpect(status().isCreated());
        assertBalance(accountA.getId(), "920.00");
    }

    @Test
    @DisplayName("6. Por role — FINANCE limitado 422; OWNER passa")
    void perRoleLimitAppliesToFinanceNotOwner() throws Exception {
        createLimit(CreateTransactionLimitRequest.builder()
                .organizationId(org.getId())
                .roleId(financeRoleId)
                .transactionType(LimitTransactionType.PIX)
                .period(LimitPeriod.PER_TRANSACTION)
                .maxAmount(new BigDecimal("50.00"))
                .build());
        stubTransfer("tr_lim_role");

        pix(finance.getId(), accountA.getId(), "80.00")
                .andExpect(status().isUnprocessableEntity());
        assertBalance(accountA.getId(), "1000.00");

        pix(owner.getId(), accountA.getId(), "80.00")
                .andExpect(status().isCreated());
        assertBalance(accountA.getId(), "920.00");
    }

    @Test
    @DisplayName("7. Por account — account A limitada 422; B não")
    void perAccountLimitDoesNotAffectOtherAccount() throws Exception {
        createLimit(CreateTransactionLimitRequest.builder()
                .organizationId(org.getId())
                .accountId(accountA.getId())
                .transactionType(LimitTransactionType.PIX)
                .period(LimitPeriod.PER_TRANSACTION)
                .maxAmount(new BigDecimal("50.00"))
                .build());
        stubTransfer("tr_lim_acc");

        pix(owner.getId(), accountA.getId(), "80.00")
                .andExpect(status().isUnprocessableEntity());
        assertBalance(accountA.getId(), "1000.00");

        pix(owner.getId(), accountB.getId(), "80.00")
                .andExpect(status().isCreated());
        assertBalance(accountB.getId(), "920.00");
    }

    @Test
    @DisplayName("8. Concorrência — capacidade residual = 1 operação: exatamente 1 sucesso")
    void concurrentDailyLimitAllowsExactlyOne() throws Exception {
        createLimit(orgWide(LimitPeriod.DAILY, "100.00"));
        stubTransfer("tr_lim_conc");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        AtomicInteger created = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        Callable<Void> task = () -> {
            try {
                mockMvc.perform(post("/api/v1/pix/transfers")
                                .header(ACTOR, owner.getId())
                                .header(IDEMPOTENCY, UUID.randomUUID().toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                        transferBody(accountA.getId(), "80.00"))))
                        .andExpect(result -> {
                            int s = result.getResponse().getStatus();
                            if (s == 201) {
                                created.incrementAndGet();
                            } else if (s == 422) {
                                rejected.incrementAndGet();
                            } else {
                                throw new AssertionError("Unexpected status " + s
                                        + " body=" + result.getResponse().getContentAsString());
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

        assertThat(created.get()).isEqualTo(1);
        assertThat(rejected.get()).isEqualTo(1);
        assertBalance(accountA.getId(), "920.00");
    }

    @Test
    @DisplayName("9. PATCH reduz teto; próxima operação 422")
    void patchLowersCapAndNextOperationFails() throws Exception {
        UUID limitId = createLimit(orgWide(LimitPeriod.PER_TRANSACTION, "100.00"));
        stubTransfer("tr_lim_patch");

        pix(owner.getId(), accountA.getId(), "80.00")
                .andExpect(status().isCreated());

        mockMvc.perform(patch("/api/v1/limits/{id}", limitId)
                        .header(ACTOR, owner.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(UpdateTransactionLimitRequest.builder()
                                .maxAmount(new BigDecimal("50.00"))
                                .build())))
                .andExpect(status().isOk());

        reset(asaasTransferClient);
        pix(owner.getId(), accountA.getId(), "80.00")
                .andExpect(status().isUnprocessableEntity());
        verify(asaasTransferClient, never()).createTransfer(anyString(), any());
    }

    @Test
    @DisplayName("10. EMPLOYEE POST/PATCH 403; GET sem limits.read 403")
    void employeeForbiddenOnManageAndRead() throws Exception {
        UUID limitId = createLimit(orgWide(LimitPeriod.PER_TRANSACTION, "100.00"));

        mockMvc.perform(post("/api/v1/limits")
                        .header(ACTOR, employee.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orgWide(LimitPeriod.DAILY, "200.00"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/v1/limits/{id}", limitId)
                        .header(ACTOR, employee.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(UpdateTransactionLimitRequest.builder()
                                .maxAmount(new BigDecimal("10.00"))
                                .build())))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/limits")
                        .header(ACTOR, employee.getId())
                        .param("organizationId", org.getId().toString()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/limits/{id}", limitId)
                        .header(ACTOR, employee.getId()))
                .andExpect(status().isForbidden());
    }

    private UUID createLimit(CreateTransactionLimitRequest request) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/limits")
                        .header(ACTOR, owner.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(
                result.getResponse().getContentAsString(), TransactionLimitResponse.class).getId();
    }

    private org.springframework.test.web.servlet.ResultActions pix(UUID actorId, UUID accountId, String amount)
            throws Exception {
        return mockMvc.perform(post("/api/v1/pix/transfers")
                .header(ACTOR, actorId)
                .header(IDEMPOTENCY, UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(transferBody(accountId, amount))));
    }

    private CreateTransactionLimitRequest orgWide(LimitPeriod period, String maxAmount) {
        return CreateTransactionLimitRequest.builder()
                .organizationId(org.getId())
                .transactionType(LimitTransactionType.PIX)
                .period(period)
                .maxAmount(new BigDecimal(maxAmount))
                .build();
    }

    private static CreatePixTransferRequest transferBody(UUID accountId, String amount) {
        return CreatePixTransferRequest.builder()
                .accountId(accountId)
                .amount(new BigDecimal(amount))
                .destinationPixKey("12345678901")
                .destinationPixKeyType(PixKeyType.CPF)
                .description("limit test")
                .build();
    }

    private void stubTransfer(String transferId) {
        when(asaasTransferClient.createTransfer(anyString(), any()))
                .thenReturn(AsaasTransferResponse.builder()
                        .id(transferId)
                        .value(new BigDecimal("80.00"))
                        .status("PENDING")
                        .operationType("PIX")
                        .build());
    }

    private void assertBalance(UUID accountId, String expected) {
        Wallet wallet = walletRepository.findByAccount_Id(accountId).orElseThrow();
        assertThat(wallet.getBalance()).isEqualByComparingTo(new BigDecimal(expected));
    }

    private void linkAsaas(UUID accountId, String cpf) {
        Subaccount sub = TestFixtures.aSubaccount(cpf, SubaccountStatus.ACTIVE);
        sub.setAsaasAccountId("asaas_acc_" + cpf);
        sub.setAsaasWalletId("asaas_wal_" + cpf);
        sub.setEncryptedApiKey(new byte[]{1, 2, 3, 4, 5, 6, 7, 8});
        sub = subaccountRepository.saveAndFlush(sub);
        jdbcTemplate.update("UPDATE subaccount SET account_id = ? WHERE id = ?", accountId, sub.getId());
    }

    private void configureAccountLimits(UUID accountId, String maxOp, String daily) {
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
        }
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
