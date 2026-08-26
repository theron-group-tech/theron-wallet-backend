package com.theron.wallet.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theron.wallet.BaseIntegrationTest;
import com.theron.wallet.TestFixtures;
import com.theron.wallet.dto.asaas.AsaasTransferResponse;
import com.theron.wallet.dto.request.AddOrganizationMemberRequest;
import com.theron.wallet.dto.request.CreateAccountRequest;
import com.theron.wallet.dto.request.CreateOrganizationRequest;
import com.theron.wallet.dto.request.CreatePaymentOrderRequest;
import com.theron.wallet.dto.request.CreatePixTransferRequest;
import com.theron.wallet.dto.request.CreateUserRequest;
import com.theron.wallet.dto.response.AccountResponse;
import com.theron.wallet.dto.response.OrganizationResponse;
import com.theron.wallet.dto.response.PaymentOrderResponse;
import com.theron.wallet.dto.response.UserResponse;
import com.theron.wallet.entity.AccountLimit;
import com.theron.wallet.entity.Subaccount;
import com.theron.wallet.entity.Wallet;
import com.theron.wallet.enums.AccountType;
import com.theron.wallet.enums.DocumentType;
import com.theron.wallet.enums.PixKeyType;
import com.theron.wallet.enums.RoleCode;
import com.theron.wallet.enums.SubaccountStatus;
import com.theron.wallet.repository.AccountLimitRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class OfficialRulesIntegrationTest extends BaseIntegrationTest {

    private static final String IDEMPOTENCY = "Idempotency-Key";

    @Autowired private UserService userService;
    @Autowired private OrganizationService organizationService;
    @Autowired private OrganizationMembershipService membershipService;
    @Autowired private RoleAssignmentService roleAssignmentService;
    @Autowired private AccountService accountService;
    @Autowired private WalletService walletService;
    @Autowired private WalletRepository walletRepository;
    @Autowired private SubaccountRepository subaccountRepository;
    @Autowired private AccountLimitRepository accountLimitRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    private OrganizationResponse org;
    private UserResponse owner;
    private UserResponse finance;
    private UserResponse employee;
    private AccountResponse ownerAccount;
    private AccountResponse financeAccount;
    private AccountResponse employeeAccount;
    private String tokenOwner;
    private String tokenFinance;
    private String tokenEmployee;
    private String adminToken;

    @BeforeEach
    void setUp() {
        org = organizationService.create(CreateOrganizationRequest.builder()
                .legalName("Official Rules Org")
                .document("99887766000155")
                .documentType(DocumentType.CNPJ)
                .build());
        owner = createUser("off-owner@theron.test");
        finance = createUser("off-finance@theron.test");
        employee = createUser("off-emp@theron.test");

        membershipService.addMember(org.getId(), member(owner.getId()));
        membershipService.addMember(org.getId(), member(finance.getId()));
        membershipService.addMember(org.getId(), member(employee.getId()));
        roleAssignmentService.assignRolesInternal(org.getId(), owner.getId(), List.of(RoleCode.OWNER.name()));
        roleAssignmentService.assignRolesInternal(org.getId(), finance.getId(), List.of(RoleCode.FINANCE.name()));
        roleAssignmentService.assignRolesInternal(org.getId(), employee.getId(), List.of(RoleCode.EMPLOYEE.name()));

        ownerAccount = accountService.create(org.getId(),
                CreateAccountRequest.builder().name("Owner").type(AccountType.MAIN).build(),
                owner.getId(), "99887766101");
        financeAccount = accountService.create(org.getId(),
                CreateAccountRequest.builder().name("Finance").type(AccountType.MAIN).build(),
                finance.getId(), "99887766102");
        employeeAccount = accountService.create(org.getId(),
                CreateAccountRequest.builder().name("Employee").type(AccountType.EMPLOYEE).build(),
                employee.getId(), "99887766103");

        linkAsaas(ownerAccount.getId(), "99887766101");
        linkAsaas(financeAccount.getId(), "99887766102");
        linkAsaas(employeeAccount.getId(), "99887766103");
        configureLimits(ownerAccount.getId());
        configureLimits(financeAccount.getId());
        configureLimits(employeeAccount.getId());
        fund(ownerAccount.getId(), "2000.00");
        fund(financeAccount.getId(), "500.00");
        fund(employeeAccount.getId(), "300.00");

        tokenOwner = productAccessToken(owner.getEmail());
        tokenFinance = productAccessToken(finance.getEmail());
        tokenEmployee = productAccessToken(employee.getEmail());
        adminToken = adminAccessToken();

        when(asaasApiKeyResolver.resolveForSubaccount(any())).thenReturn("encrypted-resolved-key");
        when(asaasTransferClient.createTransfer(any(), any())).thenReturn(
                AsaasTransferResponse.builder().id("tr_official").status("PENDING").build());
    }

    @Test
    @DisplayName("PIX OWNER/FINANCE/EMPLOYEE always PROCESSING (no approval hold)")
    void pixAlwaysProcessingWithoutApproval() throws Exception {
        mockMvc.perform(post("/api/v1/pix/transfers")
                        .header("Authorization", bearer(tokenOwner))
                        .header(IDEMPOTENCY, UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pix(ownerAccount.getId(), "25.00"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PROCESSING"));

        mockMvc.perform(post("/api/v1/pix/transfers")
                        .header("Authorization", bearer(tokenFinance))
                        .header(IDEMPOTENCY, UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pix(financeAccount.getId(), "25.00"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PROCESSING"));

        mockMvc.perform(post("/api/v1/pix/transfers")
                        .header("Authorization", bearer(tokenEmployee))
                        .header(IDEMPOTENCY, UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pix(employeeAccount.getId(), "25.00"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PROCESSING"));
    }

    @Test
    @DisplayName("PaymentOrder: EMPLOYEE 403; FINANCE create/cancel; cannot approve; OWNER approves")
    void paymentOrderWorkflow() throws Exception {
        mockMvc.perform(post("/api/v1/payment-orders")
                        .header("Authorization", bearer(tokenEmployee))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(paymentOrder(employeeAccount.getId(), "50.00"))))
                .andExpect(status().isForbidden());

        MvcResult created = mockMvc.perform(post("/api/v1/payment-orders")
                        .header("Authorization", bearer(tokenFinance))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(paymentOrder(financeAccount.getId(), "100.00"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
                .andReturn();
        UUID orderId = objectMapper.readValue(
                created.getResponse().getContentAsString(), PaymentOrderResponse.class).getId();

        mockMvc.perform(post("/api/v1/payment-orders/{id}/approve", orderId)
                        .header("Authorization", bearer(tokenFinance))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/payment-orders/{id}/cancel", orderId)
                        .header("Authorization", bearer(tokenFinance)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        MvcResult pending = mockMvc.perform(post("/api/v1/payment-orders")
                        .header("Authorization", bearer(tokenFinance))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(paymentOrder(employeeAccount.getId(), "200.00"))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID approveId = objectMapper.readValue(
                pending.getResponse().getContentAsString(), PaymentOrderResponse.class).getId();

        mockMvc.perform(post("/api/v1/payment-orders/{id}/approve", approveId)
                        .header("Authorization", bearer(tokenOwner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        assertThat(walletRepository.findByAccount_Id(ownerAccount.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo(new BigDecimal("1800.00"));
        assertThat(walletRepository.findByAccount_Id(employeeAccount.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo(new BigDecimal("500.00"));

        mockMvc.perform(post("/api/v1/payment-orders")
                        .header("Authorization", bearer(tokenFinance))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(paymentOrder(financeAccount.getId(), "99999.00"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));
    }

    @Test
    @DisplayName("OWNER cannot read EMPLOYEE wallet → 403")
    void ownerCannotReadEmployeeWallet() throws Exception {
        UUID employeeWalletId = walletRepository.findByAccount_Id(employeeAccount.getId()).orElseThrow().getId();
        mockMvc.perform(get("/api/v1/wallets/{id}", employeeWalletId)
                        .header("Authorization", bearer(tokenOwner)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/accounts/{id}", employeeAccount.getId())
                        .header("Authorization", bearer(tokenOwner)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /roles excludes ADMIN/AUDITOR")
    void rolesCatalogExcludesAdminAuditor() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/roles")
                        .header("Authorization", bearer(tokenOwner)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode roles = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(roles.isArray()).isTrue();
        for (JsonNode role : roles) {
            String code = role.get("code").asText();
            assertThat(code).isNotIn("ADMIN", "AUDITOR");
        }
        assertThat(roles.toString()).contains("FINANCE").contains("EMPLOYEE");
    }

    @Test
    @DisplayName("Admin splits enabled by default; platform-account returns master wallet")
    void adminSplitsAndPlatformAccount() throws Exception {
        mockMvc.perform(get("/api/v1/admin/splits")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));

        mockMvc.perform(get("/api/v1/admin/platform-account")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").value("Theron Platform"))
                .andExpect(jsonPath("$.asaasMasterWalletId").value("master-wallet-test"));
    }

    private CreatePixTransferRequest pix(UUID accountId, String amount) {
        return CreatePixTransferRequest.builder()
                .accountId(accountId)
                .amount(new BigDecimal(amount))
                .destinationPixKey("12345678901")
                .destinationPixKeyType(PixKeyType.CPF)
                .description("official pix")
                .build();
    }

    private CreatePaymentOrderRequest paymentOrder(UUID destinationAccountId, String amount) {
        return CreatePaymentOrderRequest.builder()
                .organizationId(org.getId())
                .destinationAccountId(destinationAccountId)
                .amount(new BigDecimal(amount))
                .description("official payment order")
                .build();
    }

    private void linkAsaas(UUID accountId, String cpf) {
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
        sub.setEncryptedApiKey(new byte[]{1, 2, 3, 4, 5, 6, 7, 8});
        sub.setStatus(SubaccountStatus.ACTIVE);
        subaccountRepository.saveAndFlush(sub);
    }

    private void configureLimits(UUID accountId) {
        jdbcTemplate.update("DELETE FROM account_limit WHERE account_id = ?", accountId);
        accountLimitRepository.saveAndFlush(AccountLimit.builder()
                .accountId(accountId)
                .maxOperationAmount(new BigDecimal("5000.00"))
                .dailyLimitAmount(new BigDecimal("10000.00"))
                .build());
    }

    private void fund(UUID accountId, String balance) {
        Wallet wallet = walletRepository.findByAccount_Id(accountId).orElseThrow();
        BigDecimal delta = new BigDecimal(balance).subtract(wallet.getBalance());
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
