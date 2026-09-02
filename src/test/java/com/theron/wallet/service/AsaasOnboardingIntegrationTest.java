package com.theron.wallet.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.theron.wallet.BaseIntegrationTest;
import com.theron.wallet.dto.asaas.AsaasAccountStatusResponse;
import com.theron.wallet.dto.asaas.AsaasWebhookPayload;
import com.theron.wallet.dto.request.AddOrganizationMemberRequest;
import com.theron.wallet.dto.request.CreateAccountRequest;
import com.theron.wallet.dto.request.CreateOrganizationRequest;
import com.theron.wallet.dto.request.CreateUserRequest;
import com.theron.wallet.dto.request.onboarding.OnboardingAccountTypeRequest;
import com.theron.wallet.dto.request.onboarding.OnboardingAddressRequest;
import com.theron.wallet.dto.request.onboarding.OnboardingBusinessRequest;
import com.theron.wallet.dto.request.onboarding.OnboardingFinancialRequest;
import com.theron.wallet.dto.request.onboarding.OnboardingPersonalRequest;
import com.theron.wallet.dto.response.AccountResponse;
import com.theron.wallet.dto.response.OrganizationResponse;
import com.theron.wallet.dto.response.UserResponse;
import com.theron.wallet.entity.AsaasOnboarding;
import com.theron.wallet.entity.Subaccount;
import com.theron.wallet.enums.AccountType;
import com.theron.wallet.enums.AsaasOnboardingStatus;
import com.theron.wallet.enums.AsaasOnboardingStep;
import com.theron.wallet.enums.AsaasPersonType;
import com.theron.wallet.enums.DocumentType;
import com.theron.wallet.enums.RoleCode;
import com.theron.wallet.enums.SubaccountStatus;
import com.theron.wallet.integration.AsaasSubaccountClient;
import com.theron.wallet.integration.AsaasAccountStatusClient;
import com.theron.wallet.repository.AsaasOnboardingRepository;
import com.theron.wallet.repository.SubaccountRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AsaasOnboardingIntegrationTest extends BaseIntegrationTest {

    private static final String VALID_CPF = "52998224725";
    private static final String VALID_CNPJ = "60746948000112";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserService userService;
    @Autowired
    private OrganizationService organizationService;
    @Autowired
    private OrganizationMembershipService membershipService;
    @Autowired
    private RoleAssignmentService roleAssignmentService;
    @Autowired
    private AccountService accountService;
    @Autowired
    private AsaasOnboardingRepository onboardingRepository;
    @Autowired
    private SubaccountRepository subaccountRepository;
    @Autowired
    private AsaasOnboardingService asaasOnboardingService;
    @Autowired
    private WebhookService webhookService;
    @Autowired
    private AsaasSubaccountClient asaasSubaccountClient;
    @Autowired
    private AsaasAccountStatusClient asaasAccountStatusClient;

    private OrganizationResponse org;
    private UserResponse owner;
    private AccountResponse account;
    private String ownerToken;

    @BeforeEach
    void setUpOwnerAccount() {
        org = organizationService.create(CreateOrganizationRequest.builder()
                .legalName("Onboarding Org")
                .document("22345678000191")
                .documentType(DocumentType.CNPJ)
                .build());
        owner = userService.create(CreateUserRequest.builder()
                .name("Onboarding Owner")
                .email("onboarding-owner-" + UUID.randomUUID() + "@theron.test")
                .password("SenhaForte1!")
                .build());
        membershipService.addMember(org.getId(), AddOrganizationMemberRequest.builder()
                .userId(owner.getId())
                .build());
        roleAssignmentService.assignRolesInternal(org.getId(), owner.getId(), List.of(RoleCode.OWNER.name()));
        account = accountService.create(org.getId(), CreateAccountRequest.builder()
                .name("Owner MAIN")
                .type(AccountType.MAIN)
                .build(), owner.getId(), null);
        ownerToken = bearer(productAccessToken(owner.getEmail()));
    }

    @Test
    @DisplayName("CPF onboarding: steps → submit → subaccount persisted")
    void cpfOnboardingComplete() throws Exception {
        startOnboarding();
        saveAccountType(AsaasPersonType.INDIVIDUAL);
        savePersonal();
        saveAddress();
        saveFinancial();

        mockMvc.perform(post("/api/v1/asaas/onboarding/submit")
                        .header("Authorization", ownerToken)
                        .header("Idempotency-Key", "cpf-submit-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.financialResourcesEnabled").value(true));

        Subaccount subaccount = subaccountRepository.findByAccount_Id(account.getId()).orElseThrow();
        assertThat(subaccount.getAsaasAccountId()).isNotBlank();
        assertThat(subaccount.getEncryptedApiKey()).isNotNull();
        assertThat(subaccount.isLegacyAutoProvisioned()).isFalse();
        assertThat(subaccount.getStatus()).isEqualTo(SubaccountStatus.ACTIVE);

        AsaasOnboarding onboarding = onboardingRepository.findByAccountId(account.getId()).orElseThrow();
        assertThat(onboarding.getStatus()).isEqualTo(AsaasOnboardingStatus.APPROVED);
        assertThat(onboarding.getPersonType()).isEqualTo(AsaasPersonType.INDIVIDUAL);
    }

    @Test
    @DisplayName("CNPJ onboarding: business data → submit")
    void cnpjOnboardingComplete() throws Exception {
        startOnboarding();
        saveAccountType(AsaasPersonType.COMPANY);
        saveBusiness();
        saveAddress();
        saveFinancial();

        mockMvc.perform(post("/api/v1/asaas/onboarding/submit")
                        .header("Authorization", ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        AsaasOnboarding onboarding = onboardingRepository.findByAccountId(account.getId()).orElseThrow();
        assertThat(onboarding.getPersonType()).isEqualTo(AsaasPersonType.COMPANY);
        assertThat(onboarding.getCurrentStep()).isEqualTo(AsaasOnboardingStep.COMPLETED);
    }

    @Test
    @DisplayName("Resume: saves address, new session continues at FINANCIAL")
    void resumeOnboarding() throws Exception {
        startOnboarding();
        saveAccountType(AsaasPersonType.INDIVIDUAL);
        savePersonal();
        saveAddress();

        mockMvc.perform(get("/api/v1/asaas/onboarding")
                        .header("Authorization", ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStep").value("FINANCIAL"))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    @DisplayName("Submit idempotency: duplicate submit does not recreate subaccount")
    void submitIdempotent() throws Exception {
        completeCpfFlowBeforeSubmit();

        mockMvc.perform(post("/api/v1/asaas/onboarding/submit")
                        .header("Authorization", ownerToken)
                        .header("Idempotency-Key", "idem-key-1"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/asaas/onboarding/submit")
                        .header("Authorization", ownerToken)
                        .header("Idempotency-Key", "idem-key-1"))
                .andExpect(status().isOk());

        verify(asaasSubaccountClient, times(1)).createSubaccount(any());
        assertThat(subaccountRepository.findByAccount_Id(account.getId())).isPresent();
    }

    @Test
    @DisplayName("Webhook APPROVED enables financial resources after pending docs")
    void webhookApprovedEnablesPix() throws Exception {
        when(asaasAccountStatusClient.getStatus(any())).thenReturn(
                AsaasAccountStatusResponse.builder()
                        .general("PENDING")
                        .commercialInfo("PENDING")
                        .build());
        when(asaasAccountStatusClient.firstOnboardingUrl(any())).thenReturn("https://sandbox.asaas.com/onboarding/test");

        completeCpfFlowBeforeSubmit();
        MvcResult submitResult = mockMvc.perform(post("/api/v1/asaas/onboarding/submit")
                        .header("Authorization", ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.financialResourcesEnabled").value(false))
                .andReturn();

        String asaasAccountId = objectMapper.readTree(submitResult.getResponse().getContentAsString())
                .get("asaasAccountId").asText();
        assertThat(asaasOnboardingService.isFinancialResourcesEnabled(account.getId())).isFalse();

        webhookService.receive("test-webhook-token", AsaasWebhookPayload.builder()
                .id("evt_" + UUID.randomUUID())
                .event("ACCOUNT_STATUS_GENERAL_APPROVAL_APPROVED")
                .account(AsaasWebhookPayload.Account.builder().id(asaasAccountId).build())
                .build());

        assertThat(asaasOnboardingService.isFinancialResourcesEnabled(account.getId())).isTrue();
        mockMvc.perform(get("/api/v1/asaas/subaccount/status")
                        .header("Authorization", ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.financialResourcesEnabled").value(true))
                .andExpect(jsonPath("$.onboardingStatus").value("APPROVED"));
    }

    @Test
    @DisplayName("User without own account cannot start onboarding")
    void securityNoOwnAccount() throws Exception {
        UserResponse outsider = userService.create(CreateUserRequest.builder()
                .name("Outsider")
                .email("outsider-" + UUID.randomUUID() + "@theron.test")
                .password("SenhaForte1!")
                .build());
        String outsiderToken = bearer(productAccessToken(outsider.getEmail()));

        mockMvc.perform(post("/api/v1/asaas/onboarding")
                        .header("Authorization", outsiderToken))
                .andExpect(status().isForbidden());
    }

    private void startOnboarding() throws Exception {
        mockMvc.perform(post("/api/v1/asaas/onboarding")
                        .header("Authorization", ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    private void saveAccountType(AsaasPersonType personType) throws Exception {
        mockMvc.perform(put("/api/v1/asaas/onboarding/account-type")
                        .header("Authorization", ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(OnboardingAccountTypeRequest.builder()
                                .personType(personType)
                                .build())))
                .andExpect(status().isOk());
    }

    private void savePersonal() throws Exception {
        mockMvc.perform(put("/api/v1/asaas/onboarding/personal")
                        .header("Authorization", ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(OnboardingPersonalRequest.builder()
                                .name("Titular PF")
                                .cpf(VALID_CPF)
                                .birthDate("1990-05-15")
                                .email(owner.getEmail())
                                .mobilePhone("11987654321")
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStep").value("ADDRESS"));
    }

    private void saveBusiness() throws Exception {
        mockMvc.perform(put("/api/v1/asaas/onboarding/business")
                        .header("Authorization", ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(OnboardingBusinessRequest.builder()
                                .cnpj(VALID_CNPJ)
                                .legalName("Empresa Teste LTDA")
                                .tradeName("Empresa Teste")
                                .email(owner.getEmail())
                                .mobilePhone("11987654321")
                                .companyType("LIMITED")
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStep").value("ADDRESS"));
    }

    private void saveAddress() throws Exception {
        mockMvc.perform(put("/api/v1/asaas/onboarding/address")
                        .header("Authorization", ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(OnboardingAddressRequest.builder()
                                .address("Av Paulista")
                                .addressNumber("1000")
                                .complement("Sala 10")
                                .province("Bela Vista")
                                .postalCode("01310100")
                                .city("São Paulo")
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStep").value("FINANCIAL"));
    }

    private void saveFinancial() throws Exception {
        mockMvc.perform(put("/api/v1/asaas/onboarding/financial")
                        .header("Authorization", ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(OnboardingFinancialRequest.builder()
                                .incomeValue(new BigDecimal("8500.00"))
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStep").value("REVIEW"));
    }

    private void completeCpfFlowBeforeSubmit() throws Exception {
        startOnboarding();
        saveAccountType(AsaasPersonType.INDIVIDUAL);
        savePersonal();
        saveAddress();
        saveFinancial();
    }
}
