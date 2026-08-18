package com.theron.wallet.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.theron.wallet.BaseIntegrationTest;
import com.theron.wallet.TestFixtures;
import com.theron.wallet.dto.asaas.AsaasTransferResponse;
import com.theron.wallet.dto.request.AddOrganizationMemberRequest;
import com.theron.wallet.dto.request.CreateAccountRequest;
import com.theron.wallet.dto.request.CreateApprovalPolicyRequest;
import com.theron.wallet.dto.request.CreateOrganizationRequest;
import com.theron.wallet.dto.request.CreatePixTransferRequest;
import com.theron.wallet.dto.request.CreateUserRequest;
import com.theron.wallet.dto.response.AccountResponse;
import com.theron.wallet.dto.response.OrganizationResponse;
import com.theron.wallet.dto.response.PixTransferResponse;
import com.theron.wallet.dto.response.UserResponse;
import com.theron.wallet.entity.AccountLimit;
import com.theron.wallet.entity.Subaccount;
import com.theron.wallet.entity.Wallet;
import com.theron.wallet.enums.AccountType;
import com.theron.wallet.enums.ApprovalRequestStatus;
import com.theron.wallet.enums.DocumentType;
import com.theron.wallet.enums.PixKeyType;
import com.theron.wallet.enums.RoleCode;
import com.theron.wallet.enums.SubaccountStatus;
import com.theron.wallet.enums.TransactionStatus;
import com.theron.wallet.repository.AccountLimitRepository;
import com.theron.wallet.repository.ApprovalRequestRepository;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ApprovalWorkflowIntegrationTest extends BaseIntegrationTest {

    private static final String IDEMPOTENCY = "Idempotency-Key";

    @Autowired private UserService userService;
    @Autowired private OrganizationService organizationService;
    @Autowired private OrganizationMembershipService membershipService;
    @Autowired private RoleAssignmentService roleAssignmentService;
    @Autowired private AccountService accountService;
    @Autowired private WalletService walletService;
    @Autowired private SubaccountRepository subaccountRepository;
    @Autowired private AccountLimitRepository accountLimitRepository;
    @Autowired private ApprovalRequestRepository approvalRequestRepository;
    @Autowired private WalletRepository walletRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    private OrganizationResponse org;
    private UserResponse owner;
    private UserResponse finance1;
    private UserResponse finance2;
    private UserResponse employee;
    private AccountResponse account;
    private String tokenOwner;
    private String tokenFinance1;
    private String tokenFinance2;
    private String tokenEmployee;

    @BeforeEach
    void setUp() {
        org = organizationService.create(CreateOrganizationRequest.builder()
                .legalName("Approval Org")
                .document("33445566000199")
                .documentType(DocumentType.CNPJ)
                .build());
        owner = createUser("appr-owner@theron.test");
        finance1 = createUser("appr-fin1@theron.test");
        finance2 = createUser("appr-fin2@theron.test");
        employee = createUser("appr-emp@theron.test");

        membershipService.addMember(org.getId(), member(owner.getId()));
        membershipService.addMember(org.getId(), member(finance1.getId()));
        membershipService.addMember(org.getId(), member(finance2.getId()));
        membershipService.addMember(org.getId(), member(employee.getId()));

        roleAssignmentService.assignRolesInternal(org.getId(), owner.getId(), List.of(RoleCode.OWNER.name()));
        roleAssignmentService.assignRolesInternal(org.getId(), finance1.getId(), List.of(RoleCode.FINANCE.name()));
        roleAssignmentService.assignRolesInternal(org.getId(), finance2.getId(), List.of(RoleCode.FINANCE.name()));
        roleAssignmentService.assignRolesInternal(org.getId(), employee.getId(), List.of(RoleCode.EMPLOYEE.name()));
        tokenOwner = productAccessToken(owner.getEmail());
        tokenFinance1 = productAccessToken(finance1.getEmail());
        tokenFinance2 = productAccessToken(finance2.getEmail());
        tokenEmployee = productAccessToken(employee.getEmail());

        account = accountService.create(org.getId(), CreateAccountRequest.builder()
                .name("Approval Account").type(AccountType.MAIN).build());
        linkAsaas(account.getId(), "33445566789");
        configureLimits(account.getId(), "5000.00", "10000.00");
        fundWallet(account.getId(), "5000.00");

        // bands: 0–99.99 → 0; 100–499.99 → 1; 500+ → 2
        createPolicy("0.00", "99.99", 0);
        createPolicy("100.00", "499.99", 1);
        createPolicy("500.00", null, 2);

        when(asaasApiKeyResolver.resolveForSubaccount(any())).thenReturn("encrypted-resolved-key");
        stubAsaasTransfer("asaas_appr_default");
    }

    @Test
    @DisplayName("1 Sem aprovação — faixa 0 → PROCESSING + Asaas")
    void noApprovalRequired_callsAsaas() throws Exception {
        reset(asaasTransferClient);
        stubAsaasTransfer("asaas_zero");

        mockMvc.perform(post("/api/v1/pix/transfers")
                        .header("Authorization", bearer(tokenOwner))
                        .header(IDEMPOTENCY, "appr-zero-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(transfer("50.00"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PROCESSING"));

        verify(asaasTransferClient, times(1)).createTransfer(any(), any());
        assertBalance("4950.00");
    }

    @Test
    @DisplayName("2 Com aprovação — faixa 1 → PENDING_APPROVAL; Asaas never")
    void holdWithoutAsaas() throws Exception {
        reset(asaasTransferClient);

        MvcResult result = mockMvc.perform(post("/api/v1/pix/transfers")
                        .header("Authorization", bearer(tokenOwner))
                        .header(IDEMPOTENCY, "appr-hold-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(transfer("150.00"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
                .andReturn();

        verify(asaasTransferClient, never()).createTransfer(any(), any());
        assertBalance("5000.00");

        PixTransferResponse pix = objectMapper.readValue(
                result.getResponse().getContentAsString(), PixTransferResponse.class);
        assertThat(approvalRequestRepository.findByTransaction_Id(pix.getTransactionId())).isPresent();
    }

    @Test
    @DisplayName("3 Aprovar — FINANCE aprova → PROCESSING + Asaas")
    void approveTriggersExecution() throws Exception {
        UUID requestId = createHeldTransfer("appr-approve-1", "150.00");
        reset(asaasTransferClient);
        stubAsaasTransfer("asaas_approve_1");

        mockMvc.perform(post("/api/v1/approvals/{id}/approve", requestId)
                        .header("Authorization", bearer(tokenFinance1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.transactionStatus").value("PROCESSING"));

        verify(asaasTransferClient, times(1)).createTransfer(any(), any());
        assertBalance("4850.00");
    }

    @Test
    @DisplayName("4 Rejeitar — REJECTED; saldo intacto; Asaas never")
    void rejectKeepsBalance() throws Exception {
        UUID requestId = createHeldTransfer("appr-reject-1", "150.00");
        reset(asaasTransferClient);

        mockMvc.perform(post("/api/v1/approvals/{id}/reject", requestId)
                        .header("Authorization", bearer(tokenFinance1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"nope\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.transactionStatus").value("CANCELLED"));

        verify(asaasTransferClient, never()).createTransfer(any(), any());
        assertBalance("5000.00");
    }

    @Test
    @DisplayName("5 Sem permission — EMPLOYEE approve → 403")
    void employeeCannotApprove() throws Exception {
        UUID requestId = createHeldTransfer("appr-emp-1", "150.00");

        mockMvc.perform(post("/api/v1/approvals/{id}/approve", requestId)
                        .header("Authorization", bearer(tokenEmployee))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("6 Self approval — requester tenta aprovar → 403")
    void selfApprovalForbidden() throws Exception {
        UUID requestId = createHeldTransfer("appr-self-1", "150.00");

        mockMvc.perform(post("/api/v1/approvals/{id}/approve", requestId)
                        .header("Authorization", bearer(tokenOwner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("7 Dois aprovadores — 1ª permanece PENDING; 2ª dispara Asaas")
    void twoApproversRequired() throws Exception {
        UUID requestId = createHeldTransfer("appr-two-1", "600.00");
        reset(asaasTransferClient);
        stubAsaasTransfer("asaas_two");

        mockMvc.perform(post("/api/v1/approvals/{id}/approve", requestId)
                        .header("Authorization", bearer(tokenFinance1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.approvedCount").value(1))
                .andExpect(jsonPath("$.transactionStatus").value("PENDING_APPROVAL"));

        verify(asaasTransferClient, never()).createTransfer(any(), any());
        assertBalance("5000.00");

        mockMvc.perform(post("/api/v1/approvals/{id}/approve", requestId)
                        .header("Authorization", bearer(tokenFinance2))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.approvedCount").value(2))
                .andExpect(jsonPath("$.transactionStatus").value("PROCESSING"));

        verify(asaasTransferClient, times(1)).createTransfer(any(), any());
        assertBalance("4400.00");
    }

    @Test
    @DisplayName("8 Duplicate approval — mesmo ator 2x → 409; count não sobe 2x")
    void duplicateApprovalConflict() throws Exception {
        UUID requestId = createHeldTransfer("appr-dup-1", "600.00");

        mockMvc.perform(post("/api/v1/approvals/{id}/approve", requestId)
                        .header("Authorization", bearer(tokenFinance1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approvedCount").value(1));

        mockMvc.perform(post("/api/v1/approvals/{id}/approve", requestId)
                        .header("Authorization", bearer(tokenFinance1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict());

        assertThat(approvalRequestRepository.findById(requestId).orElseThrow().getApprovedCount())
                .isEqualTo(1);
        assertThat(approvalRequestRepository.findById(requestId).orElseThrow().getStatus())
                .isEqualTo(ApprovalRequestStatus.PENDING);
    }

    @Test
    @DisplayName("9 Expiração — expires_at passado → approve 422; status EXPIRED")
    void expiredRequest() throws Exception {
        UUID requestId = createHeldTransfer("appr-exp-1", "150.00");
        jdbcTemplate.update(
                "UPDATE approval_request SET expires_at = NOW() - INTERVAL '1 hour' WHERE id = ?",
                requestId);

        mockMvc.perform(post("/api/v1/approvals/{id}/approve", requestId)
                        .header("Authorization", bearer(tokenFinance1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity());

        assertThat(approvalRequestRepository.findById(requestId).orElseThrow().getStatus())
                .isEqualTo(ApprovalRequestStatus.EXPIRED);
        assertBalance("5000.00");
    }

    @Test
    @DisplayName("10 Cancelamento — requester cancela → CANCELLED; Asaas never")
    void requesterCancels() throws Exception {
        UUID requestId = createHeldTransfer("appr-cancel-1", "150.00");
        reset(asaasTransferClient);

        mockMvc.perform(post("/api/v1/approvals/{id}/cancel", requestId)
                        .header("Authorization", bearer(tokenOwner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.transactionStatus").value("CANCELLED"));

        verify(asaasTransferClient, never()).createTransfer(any(), any());
        assertBalance("5000.00");
    }

    @Test
    @DisplayName("11 Concorrência — 2 threads approve (required=1): exatamente 1 Asaas")
    void concurrentApproveExactlyOneExecution() throws Exception {
        UUID requestId = createHeldTransfer("appr-conc-1", "150.00");
        reset(asaasTransferClient);
        stubAsaasTransfer("asaas_conc");

        AtomicInteger successes = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Void> fin1 = () -> {
                int status = mockMvc.perform(post("/api/v1/approvals/{id}/approve", requestId)
                                .header("Authorization", bearer(tokenFinance1))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                        .andReturn()
                        .getResponse()
                        .getStatus();
                if (status == 200) {
                    successes.incrementAndGet();
                } else {
                    conflicts.incrementAndGet();
                }
                return null;
            };
            Callable<Void> fin2 = () -> {
                int status = mockMvc.perform(post("/api/v1/approvals/{id}/approve", requestId)
                                .header("Authorization", bearer(tokenFinance2))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                        .andReturn()
                        .getResponse()
                        .getStatus();
                if (status == 200) {
                    successes.incrementAndGet();
                } else {
                    conflicts.incrementAndGet();
                }
                return null;
            };
            List<Future<Void>> futures = pool.invokeAll(List.of(fin1, fin2));
            for (Future<Void> f : futures) {
                f.get();
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(successes.get()).isEqualTo(1);
        assertThat(conflicts.get()).isEqualTo(1);
        verify(asaasTransferClient, times(1)).createTransfer(any(), any());
        assertThat(approvalRequestRepository.findById(requestId).orElseThrow().getApprovedCount())
                .isEqualTo(1);
        assertBalance("4850.00");
    }

    private UUID createHeldTransfer(String idempotencyKey, String amount) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/pix/transfers")
                        .header("Authorization", bearer(tokenOwner))
                        .header(IDEMPOTENCY, idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(transfer(amount))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
                .andReturn();
        PixTransferResponse pix = objectMapper.readValue(
                result.getResponse().getContentAsString(), PixTransferResponse.class);
        return approvalRequestRepository.findByTransaction_Id(pix.getTransactionId())
                .orElseThrow()
                .getId();
    }

    private void createPolicy(String min, String max, int required) {
        try {
            mockMvc.perform(post("/api/v1/approvals/policies")
                            .header("Authorization", bearer(tokenOwner))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(CreateApprovalPolicyRequest.builder()
                                    .accountId(account.getId())
                                    .amountMin(new BigDecimal(min))
                                    .amountMax(max == null ? null : new BigDecimal(max))
                                    .requiredApprovals(required)
                                    .build())))
                    .andExpect(status().isCreated());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private CreatePixTransferRequest transfer(String amount) {
        return CreatePixTransferRequest.builder()
                .accountId(account.getId())
                .amount(new BigDecimal(amount))
                .destinationPixKey("12345678901")
                .destinationPixKeyType(PixKeyType.CPF)
                .description("approval test")
                .build();
    }

    private void stubAsaasTransfer(String id) {
        when(asaasTransferClient.createTransfer(any(), any())).thenReturn(
                AsaasTransferResponse.builder().id(id).status("PENDING").build());
    }

    private void assertBalance(String expected) {
        Wallet wallet = walletRepository.findByAccount_Id(account.getId()).orElseThrow();
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

    private void configureLimits(UUID accountId, String maxOp, String daily) {
        jdbcTemplate.update("DELETE FROM account_limit WHERE account_id = ?", accountId);
        accountLimitRepository.saveAndFlush(AccountLimit.builder()
                .accountId(accountId)
                .maxOperationAmount(new BigDecimal(maxOp))
                .dailyLimitAmount(new BigDecimal(daily))
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
