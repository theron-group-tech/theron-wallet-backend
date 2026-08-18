package com.theron.wallet.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.theron.wallet.BaseIntegrationTest;
import com.theron.wallet.TestFixtures;
import com.theron.wallet.dto.request.AddOrganizationMemberRequest;
import com.theron.wallet.dto.request.CreateAccountRequest;
import com.theron.wallet.dto.request.CreateOrganizationRequest;
import com.theron.wallet.dto.request.CreateUserRequest;
import com.theron.wallet.dto.request.UpdateAccountRequest;
import com.theron.wallet.dto.response.AccountResponse;
import com.theron.wallet.dto.response.OrganizationResponse;
import com.theron.wallet.dto.response.UserResponse;
import com.theron.wallet.dto.response.WalletResponse;
import com.theron.wallet.entity.Subaccount;
import com.theron.wallet.enums.AccountStatus;
import com.theron.wallet.enums.AccountType;
import com.theron.wallet.enums.DocumentType;
import com.theron.wallet.enums.RoleCode;
import com.theron.wallet.enums.SubaccountStatus;
import com.theron.wallet.exception.ResourceNotFoundException;
import com.theron.wallet.repository.SubaccountRepository;
import com.theron.wallet.repository.WalletRepository;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AccountServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private AccountService accountService;

    @Autowired
    private OrganizationService organizationService;

    @Autowired
    private WalletService walletService;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private SubaccountRepository subaccountRepository;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private OrganizationMembershipService membershipService;

    @Autowired
    private RoleAssignmentService roleAssignmentService;

    private String ownerToken(OrganizationResponse org) {
        UserResponse user = userService.create(CreateUserRequest.builder()
                .name("Account Owner")
                .email("acc-owner-" + org.getId() + "@theron.test")
                .password("SenhaForte1!")
                .build());
        membershipService.addMember(org.getId(), AddOrganizationMemberRequest.builder()
                .userId(user.getId())
                .build());
        roleAssignmentService.assignRolesInternal(org.getId(), user.getId(), List.of(RoleCode.OWNER.name()));
        return productAccessToken(user.getEmail());
    }

    @Test
    @DisplayName("1. create MAIN account with zero-balance wallet")
    void createMainAccount() throws Exception {
        OrganizationResponse org = createOrg("11122233000101");
        String token = ownerToken(org);

        MvcResult result = mockMvc.perform(post("/api/v1/organizations/{organizationId}/accounts", org.getId())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CreateAccountRequest.builder()
                                .name("Conta principal")
                                .type(AccountType.MAIN)
                                .build())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.organizationId").value(org.getId().toString()))
                .andExpect(jsonPath("$.name").value("Conta principal"))
                .andExpect(jsonPath("$.type").value("MAIN"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.currency").value("BRL"))
                .andReturn();

        AccountResponse created = objectMapper.readValue(result.getResponse().getContentAsString(), AccountResponse.class);
        WalletResponse wallet = accountService.findWallet(created.getId());
        assertThat(wallet.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(wallet.getAccountId()).isEqualTo(created.getId());
        assertThat(wallet.getSubaccountId()).isNull();
        assertThat(wallet.getCurrency()).isEqualTo("BRL");
    }

    @Test
    @DisplayName("2. list accounts scoped to organization")
    void listAccountsByOrganization() {
        OrganizationResponse org = createOrg("11122233000102");
        accountService.create(org.getId(), CreateAccountRequest.builder().name("Main").type(AccountType.MAIN).build());
        accountService.create(org.getId(), CreateAccountRequest.builder().name("Reserve").type(AccountType.RESERVE).build());

        List<AccountResponse> list = accountService.listByOrganization(org.getId());
        assertThat(list).hasSize(2);
        assertThat(list).extracting(AccountResponse::getType)
                .containsExactlyInAnyOrder(AccountType.MAIN, AccountType.RESERVE);
    }

    @Test
    @DisplayName("3. get account by id")
    void getAccountById() {
        OrganizationResponse org = createOrg("11122233000103");
        AccountResponse created = accountService.create(org.getId(),
                CreateAccountRequest.builder().name("Employee").type(AccountType.EMPLOYEE).build());

        AccountResponse found = accountService.findById(created.getId());
        assertThat(found.getId()).isEqualTo(created.getId());
        assertThat(found.getName()).isEqualTo("Employee");
        assertThat(found.getType()).isEqualTo(AccountType.EMPLOYEE);
    }

    @Test
    @DisplayName("4. cross-tenant list does not leak accounts")
    void crossTenantIsolation() throws Exception {
        OrganizationResponse orgA = createOrg("11122233000104");
        OrganizationResponse orgB = createOrg("11122233000105");
        AccountResponse accountA = accountService.create(orgA.getId(),
                CreateAccountRequest.builder().name("A-main").type(AccountType.MAIN).build());
        String tokenA = ownerToken(orgA);

        mockMvc.perform(get("/api/v1/organizations/{organizationId}/accounts", orgB.getId())
                        .header("Authorization", bearer(tokenA)))
                .andExpect(status().isForbidden());

        List<AccountResponse> fromA = accountService.listByOrganization(orgA.getId());
        assertThat(fromA).extracting(AccountResponse::getId).containsExactly(accountA.getId());
    }

    @Test
    @DisplayName("5. missing organization returns 404")
    void missingOrganization() throws Exception {
        OrganizationResponse org = createOrg("11122233000106");
        String token = ownerToken(org);
        mockMvc.perform(post("/api/v1/organizations/{organizationId}/accounts", UUID.randomUUID())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CreateAccountRequest.builder()
                                .name("Orphan")
                                .type(AccountType.MAIN)
                                .build())))
                .andExpect(status().isNotFound());

        assertThatThrownBy(() -> accountService.listByOrganization(UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("6. PATCH status is persisted")
    void patchStatus() throws Exception {
        OrganizationResponse org = createOrg("11122233000106");
        String token = ownerToken(org);
        AccountResponse created = accountService.create(org.getId(),
                CreateAccountRequest.builder().name("Status").type(AccountType.MAIN).build());

        mockMvc.perform(patch("/api/v1/accounts/{id}", created.getId())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(UpdateAccountRequest.builder()
                                .status(AccountStatus.SUSPENDED)
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));

        mockMvc.perform(patch("/api/v1/accounts/{id}", created.getId())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(UpdateAccountRequest.builder()
                                .status(AccountStatus.CLOSED)
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));

        assertThat(accountService.findById(created.getId()).getStatus()).isEqualTo(AccountStatus.CLOSED);
    }

    @Test
    @DisplayName("7. GET wallet associated to account")
    void getAssociatedWallet() throws Exception {
        OrganizationResponse org = createOrg("11122233000107");
        String token = ownerToken(org);
        AccountResponse created = accountService.create(org.getId(),
                CreateAccountRequest.builder().name("With wallet").type(AccountType.MAIN).build());

        mockMvc.perform(get("/api/v1/accounts/{id}/wallet", created.getId())
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(created.getId().toString()))
                .andExpect(jsonPath("$.balance").value(0))
                .andExpect(jsonPath("$.subaccountId").doesNotExist());
    }

    @Test
    @DisplayName("8. Asaas secrets are not exposed on account or wallet JSON")
    void secretsNotExposed() throws Exception {
        OrganizationResponse org = createOrg("11122233000108");
        String token = ownerToken(org);
        AccountResponse created = accountService.create(org.getId(),
                CreateAccountRequest.builder().name("Secrets").type(AccountType.MAIN).build());

        String accountJson = mockMvc.perform(get("/api/v1/accounts/{id}", created.getId())
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String walletJson = mockMvc.perform(get("/api/v1/accounts/{id}/wallet", created.getId())
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(accountJson.toLowerCase())
                .doesNotContain("encryptedapikey")
                .doesNotContain("apikey")
                .doesNotContain("webhooktoken")
                .doesNotContain("password");
        assertThat(walletJson.toLowerCase())
                .doesNotContain("encryptedapikey")
                .doesNotContain("apikey")
                .doesNotContain("webhooktoken")
                .doesNotContain("password");
    }

    @Test
    @DisplayName("9. legacy wallet by subaccount still works without accountId")
    void legacyWalletCompatibility() throws Exception {
        Subaccount subaccount = subaccountRepository.save(TestFixtures.aSubaccount(SubaccountStatus.ACTIVE));
        WalletResponse created = walletService.getOrCreateWallet(subaccount.getId());

        assertThat(created.getSubaccountId()).isEqualTo(subaccount.getId());
        assertThat(created.getAccountId()).isNull();
        assertThat(walletRepository.findBySubaccountId(subaccount.getId())).isPresent();

        mockMvc.perform(get("/api/v1/wallets/subaccount/{subaccountId}", subaccount.getId())
                        .header("Authorization", bearer(productAccessToken(
                                userService.create(CreateUserRequest.builder()
                                        .name("Legacy")
                                        .email("legacy-wallet@theron.test")
                                        .password("SenhaForte1!")
                                        .build()).getEmail()))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("10. regression: credit/debit still work on legacy wallet")
    void legacyCreditDebitRegression() {
        Subaccount subaccount = subaccountRepository.save(TestFixtures.aSubaccount(SubaccountStatus.ACTIVE));
        WalletResponse wallet = walletService.getOrCreateWallet(subaccount.getId());

        walletService.credit(wallet.getId(), new BigDecimal("50.00"));
        walletService.debit(wallet.getId(), new BigDecimal("20.00"));

        WalletResponse updated = walletService.findById(wallet.getId());
        assertThat(updated.getBalance()).isEqualByComparingTo(new BigDecimal("30.00"));
        assertThat(updated.getSubaccountId()).isEqualTo(subaccount.getId());
        assertThat(updated.getAccountId()).isNull();
    }

    private OrganizationResponse createOrg(String document) {
        return organizationService.create(CreateOrganizationRequest.builder()
                .legalName("Org " + document)
                .document(document)
                .documentType(DocumentType.CNPJ)
                .build());
    }
}
