package com.theron.wallet.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.theron.wallet.BaseIntegrationTest;
import com.theron.wallet.dto.request.CreateOauthClientRequest;
import com.theron.wallet.dto.request.onboarding.B2bAsaasOnboardingSubmitRequest;
import com.theron.wallet.dto.request.onboarding.OnboardingAddressRequest;
import com.theron.wallet.dto.request.onboarding.OnboardingFinancialRequest;
import com.theron.wallet.dto.request.onboarding.OnboardingPersonalRequest;
import com.theron.wallet.dto.response.OauthClientSecretResponse;
import com.theron.wallet.dto.response.OauthTokenResponse;
import com.theron.wallet.entity.Account;
import com.theron.wallet.entity.Organization;
import com.theron.wallet.entity.User;
import com.theron.wallet.entity.Wallet;
import com.theron.wallet.enums.AccountStatus;
import com.theron.wallet.enums.AccountType;
import com.theron.wallet.enums.AsaasPersonType;
import com.theron.wallet.enums.DocumentType;
import com.theron.wallet.enums.OauthClientEnvironment;
import com.theron.wallet.enums.OrganizationStatus;
import com.theron.wallet.enums.UserStatus;
import com.theron.wallet.repository.AccountRepository;
import com.theron.wallet.repository.OrganizationRepository;
import com.theron.wallet.repository.SubaccountRepository;
import com.theron.wallet.repository.UserRepository;
import com.theron.wallet.repository.WalletRepository;
import com.theron.wallet.security.PermissionCodes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class B2bAsaasOnboardingIntegrationTest extends BaseIntegrationTest {

    private static final String VALID_CPF = "52998224725";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private OauthClientAdminService oauthClientAdminService;
    @Autowired private OauthTokenService oauthTokenService;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private WalletRepository walletRepository;
    @Autowired private SubaccountRepository subaccountRepository;
    @Autowired private LedgerService ledgerService;

    private Account accountA;
    private Account accountB;
    private OauthClientSecretResponse clientACreds;
    private OauthClientSecretResponse clientBCreds;
    private String tokenA;
    private String tokenB;

    @BeforeEach
    void setUp() {
        Organization orgA = organizationRepository.save(Organization.builder()
                .legalName("Org A B2B Onboarding")
                .document(uniqueDigits(14))
                .documentType(DocumentType.CNPJ)
                .status(OrganizationStatus.ACTIVE)
                .build());
        Organization orgB = organizationRepository.save(Organization.builder()
                .legalName("Org B B2B Onboarding")
                .document(uniqueDigits(14))
                .documentType(DocumentType.CNPJ)
                .status(OrganizationStatus.ACTIVE)
                .build());

        User ownerA = userRepository.save(User.builder()
                .name("Owner A").email("b2b-ownera-" + UUID.randomUUID() + "@test.local")
                .passwordHash("$2a$10$abcdefghijklmnopqrstuu").status(UserStatus.ACTIVE).build());
        User ownerB = userRepository.save(User.builder()
                .name("Owner B").email("b2b-ownerb-" + UUID.randomUUID() + "@test.local")
                .passwordHash("$2a$10$abcdefghijklmnopqrstuu").status(UserStatus.ACTIVE).build());

        accountA = accountRepository.save(Account.builder()
                .organization(orgA).ownerUser(ownerA).name("Acc A")
                .type(AccountType.MAIN).status(AccountStatus.ACTIVE).currency("BRL").build());
        accountB = accountRepository.save(Account.builder()
                .organization(orgB).ownerUser(ownerB).name("Acc B")
                .type(AccountType.MAIN).status(AccountStatus.ACTIVE).currency("BRL").build());

        walletRepository.save(Wallet.builder().account(accountA).balance(BigDecimal.ZERO).currency("BRL").build());
        walletRepository.save(Wallet.builder().account(accountB).balance(BigDecimal.ZERO).currency("BRL").build());
        ledgerService.provisionForAccount(accountA);
        ledgerService.provisionForAccount(accountB);

        List<String> scopes = List.of(
                PermissionCodes.ONBOARDING_READ,
                PermissionCodes.ONBOARDING_SUBMIT,
                PermissionCodes.WALLET_READ);

        clientACreds = oauthClientAdminService.create(orgA.getId(),
                CreateOauthClientRequest.builder()
                        .name("Onboarding Client A")
                        .environment(OauthClientEnvironment.SANDBOX)
                        .scopes(scopes)
                        .accountIds(List.of(accountA.getId()))
                        .build(), null);
        clientBCreds = oauthClientAdminService.create(orgB.getId(),
                CreateOauthClientRequest.builder()
                        .name("Onboarding Client B")
                        .environment(OauthClientEnvironment.SANDBOX)
                        .scopes(scopes)
                        .accountIds(List.of(accountB.getId()))
                        .build(), null);

        tokenA = bearerToken(clientACreds);
        tokenB = bearerToken(clientBCreds);
    }

    @Test
    @DisplayName("B2B one-shot CPF onboarding creates subaccount")
    void b2bOneShotCpfSubmit() throws Exception {
        mockMvc.perform(get("/api/v1/asaas/onboarding/b2b")
                        .header(HttpHeaders.AUTHORIZATION, tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NOT_STARTED"));

        mockMvc.perform(post("/api/v1/asaas/onboarding/b2b")
                        .header(HttpHeaders.AUTHORIZATION, tokenA)
                        .header("Idempotency-Key", "b2b-cpf-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleIndividualRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.financialResourcesEnabled").value(true))
                .andExpect(jsonPath("$.asaasAccountId").isNotEmpty());

        assertThat(subaccountRepository.findByAccount_Id(accountA.getId())).isPresent();

        mockMvc.perform(get("/api/v1/asaas/subaccount/status/b2b")
                        .header(HttpHeaders.AUTHORIZATION, tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.financialResourcesEnabled").value(true))
                .andExpect(jsonPath("$.hasSubaccount").value(true));
    }

    @Test
    @DisplayName("B2B submit is idempotent with same Idempotency-Key")
    void b2bSubmitIdempotent() throws Exception {
        B2bAsaasOnboardingSubmitRequest body = sampleIndividualRequest();

        mockMvc.perform(post("/api/v1/asaas/onboarding/b2b")
                        .header(HttpHeaders.AUTHORIZATION, tokenA)
                        .header("Idempotency-Key", "b2b-idem-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/asaas/onboarding/b2b")
                        .header(HttpHeaders.AUTHORIZATION, tokenA)
                        .header("Idempotency-Key", "b2b-idem-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());

        verify(asaasSubaccountClient, times(1)).createSubaccount(any());
    }

    @Test
    @DisplayName("B2B submit without Idempotency-Key returns 400")
    void b2bRequiresIdempotencyKey() throws Exception {
        mockMvc.perform(post("/api/v1/asaas/onboarding/b2b")
                        .header(HttpHeaders.AUTHORIZATION, tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleIndividualRequest())))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("Client B cannot onboard Account A (bound account isolation)")
    void b2bIsolationAcrossClients() throws Exception {
        mockMvc.perform(post("/api/v1/asaas/onboarding/b2b")
                        .header(HttpHeaders.AUTHORIZATION, tokenA)
                        .header("Idempotency-Key", "b2b-a-only")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleIndividualRequest())))
                .andExpect(status().isOk());

        // Client B submits for its own bound account — succeeds independently (not A's data)
        mockMvc.perform(get("/api/v1/asaas/subaccount/status/b2b")
                        .header(HttpHeaders.AUTHORIZATION, tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(accountB.getId().toString()))
                .andExpect(jsonPath("$.hasSubaccount").value(false));

        mockMvc.perform(get("/api/v1/asaas/subaccount/status/b2b")
                        .header(HttpHeaders.AUTHORIZATION, tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(accountA.getId().toString()))
                .andExpect(jsonPath("$.hasSubaccount").value(true));
    }

    @Test
    @DisplayName("Missing onboarding.submit scope returns 403")
    void b2bMissingSubmitScope() throws Exception {
        Organization org = organizationRepository.save(Organization.builder()
                .legalName("Org ReadOnly Onboarding")
                .document(uniqueDigits(14))
                .documentType(DocumentType.CNPJ)
                .status(OrganizationStatus.ACTIVE)
                .build());
        User owner = userRepository.save(User.builder()
                .name("RO").email("b2b-ro-" + UUID.randomUUID() + "@test.local")
                .passwordHash("$2a$10$abcdefghijklmnopqrstuu").status(UserStatus.ACTIVE).build());
        Account account = accountRepository.save(Account.builder()
                .organization(org).ownerUser(owner).name("RO Acc")
                .type(AccountType.MAIN).status(AccountStatus.ACTIVE).currency("BRL").build());
        walletRepository.save(Wallet.builder().account(account).balance(BigDecimal.ZERO).currency("BRL").build());
        ledgerService.provisionForAccount(account);

        OauthClientSecretResponse readOnly = oauthClientAdminService.create(org.getId(),
                CreateOauthClientRequest.builder()
                        .name("Read only onboarding")
                        .environment(OauthClientEnvironment.SANDBOX)
                        .scopes(List.of(PermissionCodes.ONBOARDING_READ))
                        .accountIds(List.of(account.getId()))
                        .build(), null);

        mockMvc.perform(post("/api/v1/asaas/onboarding/b2b")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(readOnly))
                        .header("Idempotency-Key", "no-scope")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleIndividualRequest())))
                .andExpect(status().isForbidden());
    }

    private String bearerToken(OauthClientSecretResponse creds) {
        OauthTokenResponse token = oauthTokenService.issueClientCredentialsToken(
                creds.getClientId(), creds.getClientSecret(), "client_credentials");
        return "Bearer " + token.getAccessToken();
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
                        .complement("Apto 1")
                        .province("Centro")
                        .postalCode("01310100")
                        .city("São Paulo")
                        .build())
                .financial(OnboardingFinancialRequest.builder()
                        .incomeValue(new BigDecimal("5000.00"))
                        .build())
                .build();
    }

    private static String uniqueDigits(int length) {
        String digits = String.valueOf(Math.abs(UUID.randomUUID().getMostSignificantBits()));
        while (digits.length() < length) {
            digits = digits + digits;
        }
        return digits.substring(0, length);
    }
}
