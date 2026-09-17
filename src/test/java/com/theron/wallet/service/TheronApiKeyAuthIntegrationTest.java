package com.theron.wallet.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.theron.wallet.BaseIntegrationTest;
import com.theron.wallet.dto.request.AddOrganizationMemberRequest;
import com.theron.wallet.dto.request.CreateAccountRequest;
import com.theron.wallet.dto.request.CreateOrganizationRequest;
import com.theron.wallet.dto.request.CreateTheronApiKeyRequest;
import com.theron.wallet.dto.request.CreateUserRequest;
import com.theron.wallet.dto.request.onboarding.B2bAsaasOnboardingSubmitRequest;
import com.theron.wallet.dto.request.onboarding.OnboardingAddressRequest;
import com.theron.wallet.dto.request.onboarding.OnboardingFinancialRequest;
import com.theron.wallet.dto.request.onboarding.OnboardingPersonalRequest;
import com.theron.wallet.dto.response.AccountResponse;
import com.theron.wallet.dto.response.OrganizationResponse;
import com.theron.wallet.dto.response.TheronApiKeyResponse;
import com.theron.wallet.dto.response.UserResponse;
import com.theron.wallet.enums.AccountType;
import com.theron.wallet.enums.AsaasPersonType;
import com.theron.wallet.enums.DocumentType;
import com.theron.wallet.enums.RoleCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class TheronApiKeyAuthIntegrationTest extends BaseIntegrationTest {

    private static final String VALID_CPF = "52998224725";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserService userService;
    @Autowired private OrganizationService organizationService;
    @Autowired private OrganizationMembershipService membershipService;
    @Autowired private RoleAssignmentService roleAssignmentService;
    @Autowired private AccountService accountService;
    @Autowired private DeveloperApiKeyService developerApiKeyService;

    private OrganizationResponse org;
    private UserResponse owner;
    private UserResponse finance;
    private String ownerToken;
    private String financeToken;
    private AccountResponse ownerAccount;

    @BeforeEach
    void setUp() {
        org = organizationService.create(CreateOrganizationRequest.builder()
                .legalName("API Key Org")
                .document(uniqueDoc())
                .documentType(DocumentType.CNPJ)
                .build());
        owner = userService.create(CreateUserRequest.builder()
                .name("Owner Key")
                .email("owner-key-" + UUID.randomUUID() + "@theron.test")
                .password("SenhaForte1!")
                .build());
        finance = userService.create(CreateUserRequest.builder()
                .name("Finance Key")
                .email("finance-key-" + UUID.randomUUID() + "@theron.test")
                .password("SenhaForte1!")
                .build());
        membershipService.addMember(org.getId(), AddOrganizationMemberRequest.builder()
                .userId(owner.getId()).build());
        membershipService.addMember(org.getId(), AddOrganizationMemberRequest.builder()
                .userId(finance.getId()).build());
        roleAssignmentService.assignRolesInternal(org.getId(), owner.getId(), List.of(RoleCode.OWNER.name()));
        roleAssignmentService.assignRolesInternal(org.getId(), finance.getId(), List.of(RoleCode.FINANCE.name()));
        ownerAccount = accountService.create(org.getId(), CreateAccountRequest.builder()
                .name("Owner MAIN")
                .type(AccountType.MAIN)
                .build(), owner.getId(), null);
        accountService.create(org.getId(), CreateAccountRequest.builder()
                .name("Finance Acc")
                .type(AccountType.EMPLOYEE)
                .build(), finance.getId(), null);
        ownerToken = bearer(productAccessToken(owner.getEmail()));
        financeToken = bearer(productAccessToken(finance.getEmail()));
    }

    @Test
    @DisplayName("OWNER creates Theron API Key; GET never returns secret; FINANCE forbidden")
    void ownerCreatesApiKeyFinanceForbidden() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/developer/api-keys")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CreateTheronApiKeyRequest.builder()
                                .name("ERP Integration")
                                .build())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.apiKey").isNotEmpty())
                .andExpect(jsonPath("$.prefix").isNotEmpty())
                .andExpect(jsonPath("$.warning").isNotEmpty())
                .andReturn();

        TheronApiKeyResponse secretOnce = objectMapper.readValue(
                created.getResponse().getContentAsString(), TheronApiKeyResponse.class);
        assertThat(secretOnce.getApiKey()).startsWith("tk_sandbox_");

        mockMvc.perform(get("/api/v1/developer/api-keys")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].apiKey").doesNotExist())
                .andExpect(jsonPath("$[0].prefix").value(secretOnce.getPrefix()));

        mockMvc.perform(post("/api/v1/developer/api-keys")
                        .header(HttpHeaders.AUTHORIZATION, financeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CreateTheronApiKeyRequest.builder()
                                .name("Nope")
                                .build())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("API Key Bearer authenticates; revoked key rejected; rotate invalidates old")
    void apiKeyAuthRotateRevoke() throws Exception {
        TheronApiKeyResponse created = developerApiKeyService.create(owner.getId(),
                CreateTheronApiKeyRequest.builder().name("Auth Key").build());
        String key = created.getApiKey();

        mockMvc.perform(get("/api/v1/accounts/{id}", ownerAccount.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + key))
                .andExpect(status().isOk());

        TheronApiKeyResponse rotated = developerApiKeyService.rotate(owner.getId(), created.getId());
        mockMvc.perform(get("/api/v1/accounts/{id}", ownerAccount.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + key))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/accounts/{id}", ownerAccount.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + rotated.getApiKey()))
                .andExpect(status().isOk());

        developerApiKeyService.revoke(owner.getId(), created.getId());
        mockMvc.perform(get("/api/v1/accounts/{id}", ownerAccount.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + rotated.getApiKey()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("OWNER JWT can list charges after requireOperatingAccount (no OAuth-only 403)")
    void ownerJwtListsCharges() throws Exception {
        mockMvc.perform(get("/api/v1/charges")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("OWNER creates Asaas subaccount via alias and reads /me")
    void ownerSubaccountAlias() throws Exception {
        mockMvc.perform(post("/api/v1/asaas/subaccounts")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .header("Idempotency-Key", "sub-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleIndividualRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.asaasAccountId").isNotEmpty());

        mockMvc.perform(get("/api/v1/asaas/subaccounts/me")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasSubaccount").value(true))
                .andExpect(jsonPath("$.approved").value(true));

        mockMvc.perform(post("/api/v1/asaas/subaccounts")
                        .header(HttpHeaders.AUTHORIZATION, financeToken)
                        .header("Idempotency-Key", "fin-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleIndividualRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("API Key with charges.read can list charges")
    void apiKeyListsCharges() throws Exception {
        TheronApiKeyResponse key = developerApiKeyService.create(owner.getId(),
                CreateTheronApiKeyRequest.builder().name("Charges Key").build());
        mockMvc.perform(get("/api/v1/charges")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + key.getApiKey()))
                .andExpect(status().isOk());
    }

    private B2bAsaasOnboardingSubmitRequest sampleIndividualRequest() {
        return B2bAsaasOnboardingSubmitRequest.builder()
                .personType(AsaasPersonType.INDIVIDUAL)
                .personal(OnboardingPersonalRequest.builder()
                        .name("Bruno Santos")
                        .cpf(VALID_CPF)
                        .birthDate("1990-01-15")
                        .email("bruno-" + UUID.randomUUID() + "@example.com")
                        .mobilePhone("11999998888")
                        .build())
                .address(OnboardingAddressRequest.builder()
                        .address("Rua das Flores")
                        .addressNumber("100")
                        .province("Centro")
                        .postalCode("01310100")
                        .city("São Paulo")
                        .build())
                .financial(OnboardingFinancialRequest.builder()
                        .incomeValue(new BigDecimal("5000.00"))
                        .build())
                .build();
    }

    private static String uniqueDoc() {
        String digits = String.valueOf(Math.abs(UUID.randomUUID().getMostSignificantBits()));
        while (digits.length() < 14) {
            digits = digits + digits;
        }
        return digits.substring(0, 14);
    }
}
