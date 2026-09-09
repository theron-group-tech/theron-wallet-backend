package com.theron.wallet.security;

import com.theron.wallet.BaseIntegrationTest;
import com.theron.wallet.dto.request.CreateOauthClientRequest;
import com.theron.wallet.dto.response.OauthClientSecretResponse;
import com.theron.wallet.dto.response.OauthTokenResponse;
import com.theron.wallet.entity.Account;
import com.theron.wallet.entity.Organization;
import com.theron.wallet.entity.User;
import com.theron.wallet.enums.AccountStatus;
import com.theron.wallet.enums.AccountType;
import com.theron.wallet.enums.DocumentType;
import com.theron.wallet.enums.OauthClientEnvironment;
import com.theron.wallet.enums.OrganizationStatus;
import com.theron.wallet.enums.UserStatus;
import com.theron.wallet.exception.OauthTokenException;
import com.theron.wallet.repository.AccountRepository;
import com.theron.wallet.repository.OrganizationRepository;
import com.theron.wallet.repository.UserRepository;
import com.theron.wallet.service.OauthClientAdminService;
import com.theron.wallet.service.OauthTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class OauthClientCredentialsIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OauthClientAdminService oauthClientAdminService;

    @Autowired
    private OauthTokenService oauthTokenService;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private UserRepository userRepository;

    private Organization organization;
    private Account account;
    private OauthClientSecretResponse created;

    @BeforeEach
    void setUp() {
        organization = organizationRepository.save(Organization.builder()
                .legalName("iFriend Org")
                .document(uniqueDigits(14))
                .documentType(DocumentType.CNPJ)
                .status(OrganizationStatus.ACTIVE)
                .build());
        User owner = userRepository.save(User.builder()
                .name("Owner")
                .email("owner-" + UUID.randomUUID() + "@test.local")
                .passwordHash("$2a$10$abcdefghijklmnopqrstuu")
                .status(UserStatus.ACTIVE)
                .build());
        account = accountRepository.save(Account.builder()
                .organization(organization)
                .ownerUser(owner)
                .name("Owner Account")
                .type(AccountType.EMPLOYEE)
                .status(AccountStatus.ACTIVE)
                .currency("BRL")
                .build());

        created = oauthClientAdminService.create(
                organization.getId(),
                CreateOauthClientRequest.builder()
                        .name("iFriend Integration")
                        .environment(OauthClientEnvironment.SANDBOX)
                        .scopes(List.of(
                                PermissionCodes.WALLET_READ,
                                PermissionCodes.PIX_READ,
                                PermissionCodes.PIX_TRANSFER,
                                PermissionCodes.ORGANIZATION_READ))
                        .accountIds(List.of(account.getId()))
                        .build(),
                null);
    }

    @Test
    @DisplayName("client_credentials issues access token; invalid secret returns RFC6749 error")
    void tokenEndpointIssuanceAndInvalidSecret() throws Exception {
        OauthTokenResponse token = oauthTokenService.issueClientCredentialsToken(
                created.getClientId(), created.getClientSecret(), "client_credentials");
        assertThat(token.getAccessToken()).isNotBlank();
        assertThat(token.getTokenType()).isEqualTo("Bearer");
        assertThat(token.getExpiresIn()).isGreaterThan(0);
        assertThat(token.getScope()).contains(PermissionCodes.WALLET_READ);

        mockMvc.perform(post("/api/v1/oauth/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "client_credentials")
                        .param("client_id", created.getClientId())
                        .param("client_secret", "wrong-secret"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid_client"));
    }

    @Test
    @DisplayName("CLIENT token can read allowed account; foreign account is rejected")
    void clientAccessControl() throws Exception {
        OauthTokenResponse token = oauthTokenService.issueClientCredentialsToken(
                created.getClientId(), created.getClientSecret(), "client_credentials");

        mockMvc.perform(get("/api/v1/accounts/{id}", account.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token.getAccessToken()))
                .andExpect(status().isOk());

        Account other = accountRepository.save(Account.builder()
                .organization(organization)
                .ownerUser(account.getOwnerUser())
                .name("Other")
                .type(AccountType.RESERVE)
                .status(AccountStatus.ACTIVE)
                .currency("BRL")
                .build());

        mockMvc.perform(get("/api/v1/accounts/{id}", other.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token.getAccessToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("revoked client is rejected immediately even with unexpired JWT")
    void revokedClientRejectedImmediately() throws Exception {
        OauthTokenResponse token = oauthTokenService.issueClientCredentialsToken(
                created.getClientId(), created.getClientSecret(), "client_credentials");

        oauthClientAdminService.revoke(organization.getId(), created.getId(), null);

        mockMvc.perform(get("/api/v1/accounts/{id}", account.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token.getAccessToken()))
                .andExpect(status().isUnauthorized());

        assertThatThrownBy(() -> oauthTokenService.issueClientCredentialsToken(
                created.getClientId(), created.getClientSecret(), "client_credentials"))
                .isInstanceOf(OauthTokenException.class);
    }

    @Test
    @DisplayName("PIX transfer without Idempotency-Key is rejected for CLIENT")
    void pixRequiresIdempotencyKey() throws Exception {
        OauthTokenResponse token = oauthTokenService.issueClientCredentialsToken(
                created.getClientId(), created.getClientSecret(), "client_credentials");

        String body = """
                {"accountId":"%s","amount":10.00,"destinationPixKey":"x@y.com","destinationPixKeyType":"EMAIL"}
                """.formatted(account.getId());

        mockMvc.perform(post("/api/v1/pix/transfers")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token.getAccessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    private static String uniqueDigits(int length) {
        String digits = String.valueOf(Math.abs(UUID.randomUUID().getMostSignificantBits()));
        if (digits.length() >= length) {
            return digits.substring(0, length);
        }
        return digits + "0".repeat(length - digits.length());
    }
}
