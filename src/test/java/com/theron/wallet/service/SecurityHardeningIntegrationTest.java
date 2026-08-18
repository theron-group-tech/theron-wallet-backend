package com.theron.wallet.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theron.wallet.BaseIntegrationTest;
import com.theron.wallet.TestFixtures;
import com.theron.wallet.dto.asaas.AsaasWebhookPayload;
import com.theron.wallet.dto.request.AddOrganizationMemberRequest;
import com.theron.wallet.dto.request.CreateAccountRequest;
import com.theron.wallet.dto.request.CreateBeneficiaryRequest;
import com.theron.wallet.dto.request.CreateOrganizationRequest;
import com.theron.wallet.dto.request.CreatePixTransferRequest;
import com.theron.wallet.dto.request.CreateUserRequest;
import com.theron.wallet.dto.request.InternalTransferRequest;
import com.theron.wallet.dto.request.LoginRequest;
import com.theron.wallet.dto.request.ReplaceMemberRolesRequest;
import com.theron.wallet.dto.request.UpdateUserRequest;
import com.theron.wallet.dto.response.AccountResponse;
import com.theron.wallet.dto.response.BeneficiaryResponse;
import com.theron.wallet.dto.response.OrganizationResponse;
import com.theron.wallet.dto.response.UserResponse;
import com.theron.wallet.entity.Subaccount;
import com.theron.wallet.entity.Wallet;
import com.theron.wallet.enums.AccountType;
import com.theron.wallet.enums.DocumentType;
import com.theron.wallet.enums.PixKeyType;
import com.theron.wallet.enums.RoleCode;
import com.theron.wallet.enums.SubaccountStatus;
import com.theron.wallet.repository.SubaccountRepository;
import com.theron.wallet.repository.TransactionRepository;
import com.theron.wallet.repository.WalletRepository;
import com.theron.wallet.security.LoginRateLimitFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class SecurityHardeningIntegrationTest extends BaseIntegrationTest {

    private static final String PASSWORD = "SenhaForte1!";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserService userService;
    @Autowired private OrganizationService organizationService;
    @Autowired private OrganizationMembershipService membershipService;
    @Autowired private RoleAssignmentService roleAssignmentService;
    @Autowired private AccountService accountService;
    @Autowired private BeneficiaryService beneficiaryService;
    @Autowired private WalletRepository walletRepository;
    @Autowired private SubaccountRepository subaccountRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private LoginRateLimitFilter loginRateLimitFilter;

    private OrganizationResponse orgA;
    private OrganizationResponse orgB;
    private UserResponse ownerA;
    private UserResponse ownerB;
    private UserResponse employeeA;
    private UserResponse auditorA;
    private AccountResponse accountA;
    private AccountResponse accountB;
    private Subaccount subA;
    private Subaccount subB;
    private String tokenA;
    private String tokenB;
    private String tokenEmployee;
    private String tokenAuditor;
    private BeneficiaryResponse beneficiaryB;
    private UUID walletAId;
    private UUID walletBId;

    @BeforeEach
    void setUp() {
        orgA = createOrg("44112233000181");
        orgB = createOrg("44112233000182");
        ownerA = createUser("sec-owner-a@theron.test");
        ownerB = createUser("sec-owner-b@theron.test");
        employeeA = createUser("sec-emp-a@theron.test");
        auditorA = createUser("sec-auditor-a@theron.test");

        addMember(orgA.getId(), ownerA.getId(), RoleCode.OWNER);
        addMember(orgB.getId(), ownerB.getId(), RoleCode.OWNER);
        addMember(orgA.getId(), employeeA.getId(), RoleCode.EMPLOYEE);
        addMember(orgA.getId(), auditorA.getId(), RoleCode.AUDITOR);

        accountA = accountService.create(orgA.getId(), CreateAccountRequest.builder()
                .name("Sec A").type(AccountType.MAIN).build());
        accountB = accountService.create(orgB.getId(), CreateAccountRequest.builder()
                .name("Sec B").type(AccountType.MAIN).build());
        subA = linkSubaccount(accountA.getId(), "44112233901");
        subB = linkSubaccount(accountB.getId(), "44112233902");
        walletAId = walletRepository.findByAccount_Id(accountA.getId()).orElseThrow().getId();
        walletBId = walletRepository.findByAccount_Id(accountB.getId()).orElseThrow().getId();

        beneficiaryB = beneficiaryService.create(ownerB.getId(), CreateBeneficiaryRequest.builder()
                .organizationId(orgB.getId())
                .name("Payee B")
                .pixKey("44112233902")
                .pixKeyType(PixKeyType.CPF)
                .build());

        tokenA = productAccessToken(ownerA.getEmail());
        tokenB = productAccessToken(ownerB.getEmail());
        tokenEmployee = productAccessToken(employeeA.getEmail());
        tokenAuditor = productAccessToken(auditorA.getEmail());
        loginRateLimitFilter.clearAttempts();
    }

    @AfterEach
    void restoreRateLimit() {
        loginRateLimitFilter.clearAttempts();
        loginRateLimitFilter.setMaxAttemptsForTests(10_000);
    }

    @Test
    @DisplayName("1 Org A cannot read Org B resources")
    void orgACannotReadOrgB() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{id}", accountB.getId())
                        .header("Authorization", bearer(tokenA)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/wallets/{id}", walletBId)
                        .header("Authorization", bearer(tokenA)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/beneficiaries/{id}", beneficiaryB.getId())
                        .header("Authorization", bearer(tokenA)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("2 Employee cannot elevate to OWNER/ADMIN")
    void employeeCannotElevate() throws Exception {
        mockMvc.perform(put("/api/v1/organizations/{organizationId}/members/{userId}/roles",
                        orgA.getId(), employeeA.getId())
                        .header("Authorization", bearer(tokenEmployee))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ReplaceMemberRolesRequest.builder()
                                .roleCodes(List.of(RoleCode.OWNER.name(), RoleCode.ADMIN.name()))
                                .build())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("3 Auditor cannot transfer")
    void auditorCannotTransfer() throws Exception {
        mockMvc.perform(post("/api/v1/transfers/internal")
                        .header("Authorization", bearer(tokenAuditor))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(InternalTransferRequest.builder()
                                .senderSubaccountId(subA.getId())
                                .receiverSubaccountId(subB.getId())
                                .amount(new BigDecimal("10.00"))
                                .build())))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/pix/transfers")
                        .header("Authorization", bearer(tokenAuditor))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CreatePixTransferRequest.builder()
                                .accountId(accountA.getId())
                                .amount(new BigDecimal("10.00"))
                                .destinationPixKey("12345678901")
                                .destinationPixKeyType(PixKeyType.CPF)
                                .build())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("4 User cannot GET/PATCH another user")
    void userCannotAccessAnotherUser() throws Exception {
        mockMvc.perform(get("/api/v1/users/{id}", ownerB.getId())
                        .header("Authorization", bearer(tokenA)))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/users/{id}", ownerB.getId())
                        .header("Authorization", bearer(tokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(UpdateUserRequest.builder()
                                .name("Hijacked")
                                .build())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("5 Account of other tenant is 403; own statement is 200")
    void accountIsolationAndOwnStatement() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{id}", accountB.getId())
                        .header("Authorization", bearer(tokenA)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/accounts/{accountId}/statement", accountA.getId())
                        .header("Authorization", bearer(tokenA)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("6 Wallet of other tenant is 403")
    void walletIsolation() throws Exception {
        mockMvc.perform(get("/api/v1/wallets/{id}", walletBId)
                        .header("Authorization", bearer(tokenA)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("7 Beneficiary of other tenant is 403")
    void beneficiaryIsolation() throws Exception {
        mockMvc.perform(get("/api/v1/beneficiaries/{id}", beneficiaryB.getId())
                        .header("Authorization", bearer(tokenA)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("8 Forged IDs and X-Actor-User-Id are ignored")
    void forgedIdsAndActorHeader() throws Exception {
        UUID forged = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/accounts/{id}", forged)
                        .header("Authorization", bearer(tokenA)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/organizations/{id}", orgB.getId())
                        .header("Authorization", bearer(tokenA)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/accounts/{id}", accountB.getId())
                        .header("Authorization", bearer(tokenA)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/wallets/{id}", walletBId)
                        .header("Authorization", bearer(tokenA)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/beneficiaries/{id}", beneficiaryB.getId())
                        .header("Authorization", bearer(tokenA)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/users/{id}", ownerB.getId())
                        .header("Authorization", bearer(tokenA)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/accounts/{id}", accountA.getId())
                        .header("X-Actor-User-Id", ownerB.getId().toString()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/users/{id}", ownerA.getId())
                        .header("Authorization", bearer(tokenA))
                        .header("X-Actor-User-Id", ownerB.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ownerA.getId().toString()));
        mockMvc.perform(get("/api/v1/users/{id}", ownerB.getId())
                        .header("Authorization", bearer(tokenA))
                        .header("X-Actor-User-Id", ownerB.getId().toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("9 Unauthenticated money APIs return 401")
    void unauthenticatedMoneyApis() throws Exception {
        mockMvc.perform(post("/api/v1/deposits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/transactions"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/subaccounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("10 Login enumeration is closed and rate limited")
    void loginEnumerationAndRateLimit() throws Exception {
        createUser("sec-login@theron.test");
        MvcResult missing = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(LoginRequest.builder()
                                .email("missing@theron.test")
                                .password(PASSWORD)
                                .build())))
                .andExpect(status().isUnauthorized())
                .andReturn();
        MvcResult wrong = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(LoginRequest.builder()
                                .email("sec-login@theron.test")
                                .password("WrongPass1!")
                                .build())))
                .andExpect(status().isUnauthorized())
                .andReturn();
        JsonNode missingJson = objectMapper.readTree(missing.getResponse().getContentAsString());
        JsonNode wrongJson = objectMapper.readTree(wrong.getResponse().getContentAsString());
        assertThat(missingJson.get("message").asText()).isEqualTo(wrongJson.get("message").asText());
        assertThat(missingJson.get("message").asText()).isEqualTo("Invalid email or password");

        loginRateLimitFilter.clearAttempts();
        loginRateLimitFilter.setMaxAttemptsForTests(3);
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(LoginRequest.builder()
                                    .email("missing@theron.test")
                                    .password(PASSWORD)
                                    .build())))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(LoginRequest.builder()
                                .email("missing@theron.test")
                                .password(PASSWORD)
                                .build())))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    @Test
    @DisplayName("11 Webhook token must match the transaction owner")
    void webhookTokenBoundToOwner() throws Exception {
        Wallet walletA = walletRepository.findById(walletAId).orElseThrow();
        String paymentId = "pay_sec_" + UUID.randomUUID().toString().substring(0, 8);
        transactionRepository.save(TestFixtures.aPendingDeposit(walletA, new BigDecimal("200.00"), paymentId));

        mockMvc.perform(post("/api/v1/webhooks/asaas")
                        .header("asaas-access-token", "totally-invalid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(paymentWebhook(paymentId))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/webhooks/asaas")
                        .header("asaas-access-token", subB.getWebhookToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(paymentWebhook(paymentId))))
                .andExpect(status().isOk());

        assertThat(walletRepository.findById(walletAId).orElseThrow().getBalance())
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(transactionRepository.findByAsaasPaymentId(paymentId).orElseThrow().getStatus().name())
                .isNotEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("12 401 has code+traceId; password validation hides rejectedValue")
    void errorEnvelopeAndPasswordRedaction() throws Exception {
        mockMvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.traceId").isString());

        MvcResult result = mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CreateUserRequest.builder()
                                .name("Weak")
                                .email("weak-pass@theron.test")
                                .password("short")
                                .build())))
                .andExpect(status().isBadRequest())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("rejectedValue");
        assertThat(body).doesNotContain("short");
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
                .password(PASSWORD)
                .build());
    }

    private void addMember(UUID orgId, UUID userId, RoleCode role) {
        membershipService.addMember(orgId, AddOrganizationMemberRequest.builder().userId(userId).build());
        roleAssignmentService.assignRolesInternal(orgId, userId, List.of(role.name()));
    }

    private Subaccount linkSubaccount(UUID accountId, String cpf) {
        Subaccount sub = TestFixtures.aSubaccount(cpf, SubaccountStatus.ACTIVE);
        sub = subaccountRepository.saveAndFlush(sub);
        Wallet wallet = walletRepository.findByAccount_Id(accountId).orElseThrow();
        wallet.setSubaccount(sub);
        walletRepository.saveAndFlush(wallet);
        return subaccountRepository.findById(sub.getId()).orElseThrow();
    }

    private static AsaasWebhookPayload paymentWebhook(String paymentId) {
        return AsaasWebhookPayload.builder()
                .id("evt_" + paymentId)
                .event("PAYMENT_CONFIRMED")
                .payment(AsaasWebhookPayload.Payment.builder()
                        .id(paymentId)
                        .value(new BigDecimal("200.00"))
                        .status("RECEIVED")
                        .build())
                .build();
    }
}
