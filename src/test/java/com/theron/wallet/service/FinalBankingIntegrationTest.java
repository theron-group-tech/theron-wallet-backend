package com.theron.wallet.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.theron.wallet.BaseIntegrationTest;
import com.theron.wallet.TestFixtures;
import com.theron.wallet.dto.asaas.AsaasTransferResponse;
import com.theron.wallet.dto.asaas.AsaasWebhookPayload;
import com.theron.wallet.dto.request.AddOrganizationMemberRequest;
import com.theron.wallet.dto.request.CreateAccountRequest;
import com.theron.wallet.dto.request.CreateApprovalPolicyRequest;
import com.theron.wallet.dto.request.CreateBeneficiaryRequest;
import com.theron.wallet.dto.request.CreateOrganizationRequest;
import com.theron.wallet.dto.request.CreatePixTransferRequest;
import com.theron.wallet.dto.request.CreateUserRequest;
import com.theron.wallet.dto.response.AccountResponse;
import com.theron.wallet.dto.response.BeneficiaryResponse;
import com.theron.wallet.dto.response.OrganizationResponse;
import com.theron.wallet.dto.response.PixTransferResponse;
import com.theron.wallet.dto.response.UserResponse;
import com.theron.wallet.entity.AccountLimit;
import com.theron.wallet.entity.LedgerEntry;
import com.theron.wallet.entity.LedgerTransaction;
import com.theron.wallet.entity.Subaccount;
import com.theron.wallet.entity.Transaction;
import com.theron.wallet.entity.Wallet;
import com.theron.wallet.enums.AccountType;
import com.theron.wallet.enums.AuditAction;
import com.theron.wallet.enums.DocumentType;
import com.theron.wallet.enums.LedgerDirection;
import com.theron.wallet.enums.PixKeyType;
import com.theron.wallet.enums.RoleCode;
import com.theron.wallet.enums.SubaccountStatus;
import com.theron.wallet.enums.TransactionStatus;
import com.theron.wallet.repository.AccountLimitRepository;
import com.theron.wallet.repository.ApprovalRequestRepository;
import com.theron.wallet.repository.AuditLogRepository;
import com.theron.wallet.repository.LedgerEntryRepository;
import com.theron.wallet.repository.LedgerTransactionRepository;
import com.theron.wallet.repository.NotificationRepository;
import com.theron.wallet.repository.SubaccountRepository;
import com.theron.wallet.repository.TransactionRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class FinalBankingIntegrationTest extends BaseIntegrationTest {

    private static final String IDEMPOTENCY = "Idempotency-Key";
    private static final String WEBHOOK_TOKEN = "test-webhook-token";
    private static final String PIX_KEY = "11987654321";
    private static final BigDecimal FUNDED = new BigDecimal("1000.00");

    @Autowired private UserService userService;
    @Autowired private OrganizationService organizationService;
    @Autowired private OrganizationMembershipService membershipService;
    @Autowired private RoleAssignmentService roleAssignmentService;
    @Autowired private AccountService accountService;
    @Autowired private WalletService walletService;
    @Autowired private LedgerService ledgerService;
    @Autowired private SubaccountRepository subaccountRepository;
    @Autowired private AccountLimitRepository accountLimitRepository;
    @Autowired private ApprovalRequestRepository approvalRequestRepository;
    @Autowired private WalletRepository walletRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private LedgerTransactionRepository ledgerTransactionRepository;
    @Autowired private LedgerEntryRepository ledgerEntryRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    private OrganizationResponse orgA;
    private OrganizationResponse orgB;
    private UserResponse joao;
    private UserResponse maria;
    private UserResponse carlos;
    private UserResponse ana;
    private AccountResponse accountA;
    private AccountResponse accountB;
    private String tokenJoao;
    private String tokenMaria;
    private String tokenCarlos;
    private String tokenAna;

    @BeforeEach
    void setUpPlatform() {
        orgA = createOrg("55112233000101");
        orgB = createOrg("55112233000102");

        joao = createUser("João", "joao.final@theron.test");
        maria = createUser("Maria", "maria.final@theron.test");
        carlos = createUser("Carlos", "carlos.final@theron.test");
        ana = createUser("Ana", "ana.final@theron.test");

        membershipService.addMember(orgA.getId(), member(joao.getId()));
        membershipService.addMember(orgA.getId(), member(carlos.getId()));
        membershipService.addMember(orgA.getId(), member(ana.getId()));
        membershipService.addMember(orgB.getId(), member(maria.getId()));

        roleAssignmentService.assignRolesInternal(orgA.getId(), joao.getId(), List.of(RoleCode.OWNER.name()));
        roleAssignmentService.assignRolesInternal(orgA.getId(), carlos.getId(), List.of(RoleCode.EMPLOYEE.name()));
        roleAssignmentService.assignRolesInternal(orgA.getId(), ana.getId(), List.of(RoleCode.FINANCE.name()));
        roleAssignmentService.assignRolesInternal(orgB.getId(), maria.getId(), List.of(RoleCode.OWNER.name()));

        accountA = accountService.create(orgA.getId(), CreateAccountRequest.builder()
                .name("Account A").type(AccountType.MAIN).build());
        accountB = accountService.create(orgB.getId(), CreateAccountRequest.builder()
                .name("Account B").type(AccountType.MAIN).build());

        linkAsaas(accountA.getId(), "55112233001");
        linkAsaas(accountB.getId(), "55112233002");
        configureLimits(accountA.getId(), "500.00", "1000.00");
        configureLimits(accountB.getId(), "500.00", "1000.00");

        tokenJoao = productAccessToken(joao.getEmail());
        tokenMaria = productAccessToken(maria.getEmail());
        tokenCarlos = productAccessToken(carlos.getEmail());
        tokenAna = productAccessToken(ana.getEmail());

        createPolicy("0.00", "99.99", 0);
        createPolicy("100.00", "499.99", 1);
        createPolicy("500.00", null, 0);

        fundWallet(accountA.getId(), FUNDED);
        fundWallet(accountB.getId(), FUNDED);

        when(asaasApiKeyResolver.resolveForSubaccount(any())).thenReturn("encrypted-resolved-key");
        stubAsaasTransfer("tr_final_fast");
    }

    @Test
    @DisplayName("M17 — jornada bancária ponta a ponta")
    void fullPlatformJourney() throws Exception {
        mockMvc.perform(get("/api/v1/me").header("Authorization", bearer(tokenJoao)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(joao.getId().toString()));

        mockMvc.perform(get("/api/v1/accounts/{id}/wallet", accountA.getId())
                        .header("Authorization", bearer(tokenJoao)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(1000.00));

        mockMvc.perform(get("/api/v1/accounts/{id}/ledger-balance", accountA.getId())
                        .header("Authorization", bearer(tokenJoao)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(1000.00));

        mockMvc.perform(get("/api/v1/me/dashboard")
                        .header("Authorization", bearer(tokenJoao))
                        .param("accountId", accountA.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(1000.00));

        mockMvc.perform(get("/api/v1/accounts/{id}/statement", accountA.getId())
                        .header("Authorization", bearer(tokenJoao)))
                .andExpect(status().isOk());

        MvcResult beneficiaryResult = mockMvc.perform(post("/api/v1/beneficiaries")
                        .header("Authorization", bearer(tokenJoao))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CreateBeneficiaryRequest.builder()
                                .organizationId(orgA.getId())
                                .name("Fornecedor João")
                                .pixKey(PIX_KEY)
                                .pixKeyType(PixKeyType.CPF)
                                .build())))
                .andExpect(status().isCreated())
                .andReturn();
        BeneficiaryResponse beneficiary = objectMapper.readValue(
                beneficiaryResult.getResponse().getContentAsString(), BeneficiaryResponse.class);

        String fastKey = "final-pix-fast";
        CreatePixTransferRequest fastBody = CreatePixTransferRequest.builder()
                .accountId(accountA.getId())
                .amount(new BigDecimal("40.00"))
                .beneficiaryId(beneficiary.getId())
                .description("PIX imediato")
                .build();

        MvcResult firstPix = mockMvc.perform(post("/api/v1/pix/transfers")
                        .header("Authorization", bearer(tokenJoao))
                        .header(IDEMPOTENCY, fastKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(fastBody)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PROCESSING"))
                .andReturn();
        PixTransferResponse fastPix = objectMapper.readValue(
                firstPix.getResponse().getContentAsString(), PixTransferResponse.class);
        assertThat(fastPix.getTransactionId()).isNotNull();
        assertThat(fastPix.getProviderReference()).isEqualTo("tr_final_fast");

        MvcResult replay = mockMvc.perform(post("/api/v1/pix/transfers")
                        .header("Authorization", bearer(tokenJoao))
                        .header(IDEMPOTENCY, fastKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(fastBody)))
                .andExpect(status().isCreated())
                .andReturn();
        PixTransferResponse replayed = objectMapper.readValue(
                replay.getResponse().getContentAsString(), PixTransferResponse.class);
        assertThat(replayed.getId()).isEqualTo(fastPix.getId());
        assertThat(replayed.getTransactionId()).isEqualTo(fastPix.getTransactionId());
        verify(asaasTransferClient, times(1)).createTransfer(any(), any());

        assertLedgerBalanced();

        mockMvc.perform(post("/api/v1/webhooks/asaas")
                        .header("asaas-access-token", WEBHOOK_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(transferWebhook("TRANSFER_DONE", "tr_final_fast"))))
                .andExpect(status().isOk());

        Transaction completed = transactionRepository.findById(fastPix.getTransactionId()).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(TransactionStatus.COMPLETED);

        mockMvc.perform(get("/api/v1/accounts/{id}/statement", accountA.getId())
                        .header("Authorization", bearer(tokenJoao)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id").value(org.hamcrest.Matchers.hasItem(fastPix.getTransactionId().toString())));

        mockMvc.perform(get("/api/v1/me/transactions")
                        .header("Authorization", bearer(tokenJoao))
                        .param("accountId", accountA.getId().toString()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/notifications")
                        .header("Authorization", bearer(tokenJoao)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].type").value(org.hamcrest.Matchers.hasItem("PIX_SENT")));

        mockMvc.perform(get("/api/v1/audit-logs")
                        .header("Authorization", bearer(tokenJoao))
                        .param("organizationId", orgA.getId().toString())
                        .param("action", "TRANSFER_CREATED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(org.hamcrest.Matchers.greaterThan(0)));
        assertThat(auditLogRepository.findByActionWithDetails(AuditAction.TRANSFER_CREATED)).isNotEmpty();
        assertThat(notificationRepository.countByType(com.theron.wallet.enums.NotificationType.PIX_SENT))
                .isGreaterThan(0);

        mockMvc.perform(get("/api/v1/accounts/{id}", accountA.getId())
                        .header("Authorization", bearer(tokenMaria)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/transactions/{id}", fastPix.getTransactionId())
                        .header("Authorization", bearer(tokenMaria)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/me/dashboard")
                        .header("Authorization", bearer(tokenMaria))
                        .param("accountId", accountA.getId().toString()))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/pix/transfers")
                        .header("Authorization", bearer(tokenCarlos))
                        .header(IDEMPOTENCY, "final-emp-denied")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CreatePixTransferRequest.builder()
                                .accountId(accountA.getId())
                                .amount(new BigDecimal("40.00"))
                                .beneficiaryId(beneficiary.getId())
                                .build())))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/pix/transfers")
                        .header("Authorization", bearer(tokenJoao))
                        .header(IDEMPOTENCY, "final-over-limit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CreatePixTransferRequest.builder()
                                .accountId(accountA.getId())
                                .amount(new BigDecimal("600.00"))
                                .beneficiaryId(beneficiary.getId())
                                .build())))
                .andExpect(status().isUnprocessableEntity());

        MvcResult heldResult = mockMvc.perform(post("/api/v1/pix/transfers")
                        .header("Authorization", bearer(tokenJoao))
                        .header(IDEMPOTENCY, "final-pix-hold")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CreatePixTransferRequest.builder()
                                .accountId(accountA.getId())
                                .amount(new BigDecimal("150.00"))
                                .beneficiaryId(beneficiary.getId())
                                .description("PIX com aprovação")
                                .build())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
                .andReturn();
        PixTransferResponse held = objectMapper.readValue(
                heldResult.getResponse().getContentAsString(), PixTransferResponse.class);
        verify(asaasTransferClient, times(1)).createTransfer(any(), any());
        UUID approvalId = approvalRequestRepository.findByTransaction_Id(held.getTransactionId())
                .orElseThrow()
                .getId();

        mockMvc.perform(post("/api/v1/approvals/{id}/approve", approvalId)
                        .header("Authorization", bearer(tokenJoao))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        stubAsaasTransfer("tr_final_hold");
        mockMvc.perform(post("/api/v1/approvals/{id}/approve", approvalId)
                        .header("Authorization", bearer(tokenAna))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.transactionStatus").value("PROCESSING"));
        verify(asaasTransferClient, times(2)).createTransfer(any(), any());

        mockMvc.perform(post("/api/v1/webhooks/asaas")
                        .header("asaas-access-token", WEBHOOK_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(transferWebhook("TRANSFER_DONE", "tr_final_hold"))))
                .andExpect(status().isOk());

        assertThat(transactionRepository.findById(held.getTransactionId()).orElseThrow().getStatus())
                .isEqualTo(TransactionStatus.COMPLETED);

        Wallet wallet = walletRepository.findByAccount_Id(accountA.getId()).orElseThrow();
        assertThat(wallet.getBalance()).isEqualByComparingTo(new BigDecimal("810.00"));
        assertThat(ledgerService.reconstructBalance(accountA.getId())).isEqualByComparingTo(wallet.getBalance());
        assertLedgerBalanced();
    }

    private void assertLedgerBalanced() {
        List<LedgerTransaction> transactions = ledgerTransactionRepository.findAll();
        assertThat(transactions).isNotEmpty();
        for (LedgerTransaction transaction : transactions) {
            List<LedgerEntry> entries = ledgerEntryRepository.findByTransaction_Id(transaction.getId());
            assertThat(entries.size()).isGreaterThanOrEqualTo(2);
            BigDecimal debit = sum(entries, LedgerDirection.DEBIT);
            BigDecimal credit = sum(entries, LedgerDirection.CREDIT);
            assertThat(debit).isEqualByComparingTo(credit);
        }
    }

    private static BigDecimal sum(List<LedgerEntry> entries, LedgerDirection direction) {
        return entries.stream()
                .filter(entry -> entry.getDirection() == direction)
                .map(LedgerEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void createPolicy(String min, String max, int required) {
        try {
            mockMvc.perform(post("/api/v1/approvals/policies")
                            .header("Authorization", bearer(tokenJoao))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(CreateApprovalPolicyRequest.builder()
                                    .accountId(accountA.getId())
                                    .amountMin(new BigDecimal(min))
                                    .amountMax(max == null ? null : new BigDecimal(max))
                                    .requiredApprovals(required)
                                    .build())))
                    .andExpect(status().isCreated());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void stubAsaasTransfer(String id) {
        when(asaasTransferClient.createTransfer(any(), any())).thenReturn(
                AsaasTransferResponse.builder().id(id).status("PENDING").build());
    }

    private static AsaasWebhookPayload transferWebhook(String event, String transferId) {
        return AsaasWebhookPayload.builder()
                .event(event)
                .transfer(AsaasWebhookPayload.Transfer.builder().id(transferId).build())
                .build();
    }

    private void linkAsaas(UUID accountId, String cpf) {
        Subaccount sub = TestFixtures.aSubaccount(cpf, SubaccountStatus.ACTIVE);
        sub.setAsaasAccountId("asaas_acc_" + cpf);
        sub.setAsaasWalletId("asaas_wal_" + cpf);
        sub.setEncryptedApiKey(new byte[]{1, 2, 3, 4, 5, 6, 7, 8});
        sub = subaccountRepository.saveAndFlush(sub);
        jdbcTemplate.update("UPDATE subaccount SET account_id = ? WHERE id = ?", accountId, sub.getId());
    }

    private void configureLimits(UUID accountId, String maxOp, String daily) {
        jdbcTemplate.update("DELETE FROM account_limit WHERE account_id = ?", accountId);
        accountLimitRepository.saveAndFlush(AccountLimit.builder()
                .accountId(accountId)
                .maxOperationAmount(new BigDecimal(maxOp))
                .dailyLimitAmount(new BigDecimal(daily))
                .build());
    }

    private void fundWallet(UUID accountId, BigDecimal balance) {
        Wallet wallet = walletRepository.findByAccount_Id(accountId).orElseThrow();
        BigDecimal delta = balance.subtract(wallet.getBalance());
        if (delta.compareTo(BigDecimal.ZERO) > 0) {
            walletService.credit(wallet.getId(), delta);
        }
    }

    private OrganizationResponse createOrg(String document) {
        return organizationService.create(CreateOrganizationRequest.builder()
                .legalName("Org " + document)
                .document(document)
                .documentType(DocumentType.CNPJ)
                .build());
    }

    private UserResponse createUser(String name, String email) {
        return userService.create(CreateUserRequest.builder()
                .name(name)
                .email(email)
                .password("SenhaForte1!")
                .build());
    }

    private static AddOrganizationMemberRequest member(UUID userId) {
        return AddOrganizationMemberRequest.builder().userId(userId).build();
    }
}
