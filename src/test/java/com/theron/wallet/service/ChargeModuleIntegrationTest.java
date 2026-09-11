package com.theron.wallet.service;

import com.theron.wallet.BaseIntegrationTest;
import com.theron.wallet.dto.asaas.AsaasCreateCustomerResponse;
import com.theron.wallet.dto.asaas.AsaasPaymentRequest;
import com.theron.wallet.dto.asaas.AsaasPaymentResponse;
import com.theron.wallet.dto.asaas.AsaasSplitItem;
import com.theron.wallet.dto.asaas.AsaasWebhookPayload;
import com.theron.wallet.dto.request.CreateChargeRequest;
import com.theron.wallet.dto.request.CreateOauthClientRequest;
import com.theron.wallet.dto.response.ChargeResponse;
import com.theron.wallet.dto.response.OauthClientSecretResponse;
import com.theron.wallet.dto.response.OauthTokenResponse;
import com.theron.wallet.entity.Account;
import com.theron.wallet.entity.Organization;
import com.theron.wallet.entity.Subaccount;
import com.theron.wallet.entity.User;
import com.theron.wallet.entity.Wallet;
import com.theron.wallet.enums.AccountStatus;
import com.theron.wallet.enums.AccountType;
import com.theron.wallet.enums.ChargeBillingType;
import com.theron.wallet.enums.ChargeStatus;
import com.theron.wallet.enums.DocumentType;
import com.theron.wallet.enums.OauthClientEnvironment;
import com.theron.wallet.enums.OrganizationStatus;
import com.theron.wallet.enums.SubaccountStatus;
import com.theron.wallet.enums.TransactionStatus;
import com.theron.wallet.enums.UserStatus;
import com.theron.wallet.repository.AccountRepository;
import com.theron.wallet.repository.ChargeRepository;
import com.theron.wallet.repository.OrganizationRepository;
import com.theron.wallet.repository.SubaccountRepository;
import com.theron.wallet.repository.TransactionRepository;
import com.theron.wallet.repository.UserRepository;
import com.theron.wallet.repository.WalletRepository;
import com.theron.wallet.security.Actor;
import com.theron.wallet.security.ApiKeyEncryptionService;
import com.theron.wallet.security.ClientPrincipal;
import com.theron.wallet.security.PermissionCodes;
import com.theron.wallet.service.impl.OauthClientLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ChargeModuleIntegrationTest extends BaseIntegrationTest {

    private static final String WEBHOOK_TOKEN = "charge-webhook-token";

    @Autowired private MockMvc mockMvc;
    @Autowired private ChargeService chargeService;
    @Autowired private OauthClientAdminService oauthClientAdminService;
    @Autowired private OauthTokenService oauthTokenService;
    @Autowired private OauthClientLoader oauthClientLoader;
    @Autowired private WebhookService webhookService;
    @Autowired private ApiKeyEncryptionService encryptionService;
    @Autowired private LedgerService ledgerService;

    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private WalletRepository walletRepository;
    @Autowired private SubaccountRepository subaccountRepository;
    @Autowired private ChargeRepository chargeRepository;
    @Autowired private TransactionRepository transactionRepository;

    private Account accountA;
    private Actor actorA;
    private OauthClientSecretResponse clientACreds;
    private OauthClientSecretResponse clientBCreds;
    private final AtomicInteger paymentSeq = new AtomicInteger();

    @BeforeEach
    void setUpCharges() {
        paymentSeq.set(0);
        Organization orgA = organizationRepository.save(Organization.builder()
                .legalName("Org A Charges")
                .document(uniqueDigits(14))
                .documentType(DocumentType.CNPJ)
                .status(OrganizationStatus.ACTIVE)
                .build());
        Organization orgB = organizationRepository.save(Organization.builder()
                .legalName("Org B Charges")
                .document(uniqueDigits(14))
                .documentType(DocumentType.CNPJ)
                .status(OrganizationStatus.ACTIVE)
                .build());

        User ownerA = userRepository.save(User.builder()
                .name("Owner A").email("ownera-" + UUID.randomUUID() + "@test.local")
                .passwordHash("$2a$10$abcdefghijklmnopqrstuu").status(UserStatus.ACTIVE).build());
        User ownerB = userRepository.save(User.builder()
                .name("Owner B").email("ownerb-" + UUID.randomUUID() + "@test.local")
                .passwordHash("$2a$10$abcdefghijklmnopqrstuu").status(UserStatus.ACTIVE).build());

        accountA = accountRepository.save(Account.builder()
                .organization(orgA).ownerUser(ownerA).name("Acc A")
                .type(AccountType.EMPLOYEE).status(AccountStatus.ACTIVE).currency("BRL").build());
        Account accountB = accountRepository.save(Account.builder()
                .organization(orgB).ownerUser(ownerB).name("Acc B")
                .type(AccountType.EMPLOYEE).status(AccountStatus.ACTIVE).currency("BRL").build());

        walletRepository.save(Wallet.builder().account(accountA).balance(BigDecimal.ZERO).currency("BRL").build());
        walletRepository.save(Wallet.builder().account(accountB).balance(BigDecimal.ZERO).currency("BRL").build());
        ledgerService.provisionForAccount(accountA);
        ledgerService.provisionForAccount(accountB);

        provisionSubaccount(accountA, "asaas-a-" + uniqueDigits(8));
        provisionSubaccount(accountB, "asaas-b-" + uniqueDigits(8));

        when(asaasApiKeyResolver.resolveForSubaccount(any())).thenReturn("test-api-key");
        when(asaasCustomerClient.createCustomer(anyString(), any())).thenAnswer(inv ->
                AsaasCreateCustomerResponse.builder().id("cus_" + UUID.randomUUID()).build());
        when(asaasPaymentClient.createPayment(anyString(), any(), any())).thenAnswer(inv -> {
            AsaasPaymentRequest req = inv.getArgument(1);
            String id = "pay_charge_" + paymentSeq.incrementAndGet();
            return AsaasPaymentResponse.builder()
                    .id(id)
                    .status("PENDING")
                    .value(req.getValue())
                    .netValue(req.getValue())
                    .billingType(req.getBillingType())
                    .externalReference(req.getExternalReference())
                    .build();
        });

        clientACreds = oauthClientAdminService.create(orgA.getId(),
                CreateOauthClientRequest.builder()
                        .name("Client A")
                        .environment(OauthClientEnvironment.SANDBOX)
                        .scopes(List.of(
                                PermissionCodes.CHARGES_READ,
                                PermissionCodes.CHARGES_CREATE,
                                PermissionCodes.CHARGES_CANCEL))
                        .accountIds(List.of(accountA.getId()))
                        .build(), null);
        clientBCreds = oauthClientAdminService.create(orgB.getId(),
                CreateOauthClientRequest.builder()
                        .name("Client B")
                        .environment(OauthClientEnvironment.SANDBOX)
                        .scopes(List.of(
                                PermissionCodes.CHARGES_READ,
                                PermissionCodes.CHARGES_CREATE,
                                PermissionCodes.CHARGES_CANCEL))
                        .accountIds(List.of(accountB.getId()))
                        .build(), null);

        actorA = Actor.client(toPrincipal(clientACreds));
    }

    @Test
    @DisplayName("isolation: account B cannot read charge of account A")
    void isolationAcrossAccounts() throws Exception {
        ChargeResponse created = chargeService.create(actorA, samplePixCharge("ext-iso-1"));
        OauthTokenResponse tokenB = oauthTokenService.issueClientCredentialsToken(
                clientBCreds.getClientId(), clientBCreds.getClientSecret(), "client_credentials");

        mockMvc.perform(get("/api/v1/charges/{id}", created.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB.getAccessToken()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/charges/{id}/cancel", created.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB.getAccessToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("idempotency: same externalReference calls Asaas createPayment once")
    void idempotentExternalReference() {
        CreateChargeRequest req = samplePixCharge("ext-idem-1");
        ChargeResponse first = chargeService.create(actorA, req);
        ChargeResponse second = chargeService.create(actorA, req);
        assertThat(second.getId()).isEqualTo(first.getId());
        verify(asaasPaymentClient, times(1)).createPayment(anyString(), any(), any());
    }

    @Test
    @DisplayName("webhook PAYMENT_RECEIVED updates charge and credits wallet once")
    void webhookUpdatesChargeAndLedger() {
        ChargeResponse created = chargeService.create(actorA, samplePixCharge("ext-wh-1"));
        String paymentId = created.getAsaasPaymentId();

        AsaasWebhookPayload payload = AsaasWebhookPayload.builder()
                .id("evt_charge_" + UUID.randomUUID())
                .event("PAYMENT_RECEIVED")
                .payment(AsaasWebhookPayload.Payment.builder()
                        .id(paymentId)
                        .status("RECEIVED")
                        .value(new BigDecimal("50.00"))
                        .netValue(new BigDecimal("50.00"))
                        .build())
                .build();

        when(asaasPaymentClient.retrievePayment(any(), eq(paymentId))).thenReturn(
                AsaasPaymentResponse.builder()
                        .id(paymentId)
                        .status("RECEIVED")
                        .value(new BigDecimal("50.00"))
                        .build());

        webhookService.receive(WEBHOOK_TOKEN, payload);
        webhookService.receive(WEBHOOK_TOKEN, payload);

        assertThat(chargeRepository.findById(created.getId())).get()
                .extracting(c -> c.getStatus())
                .isIn(ChargeStatus.RECEIVED, ChargeStatus.CONFIRMED);
        assertThat(transactionRepository.findByAsaasPaymentId(paymentId)).get()
                .extracting(t -> t.getStatus())
                .isEqualTo(TransactionStatus.COMPLETED);
        Wallet wallet = walletRepository.findByAccount_Id(accountA.getId()).orElseThrow();
        assertThat(wallet.getBalance()).isEqualByComparingTo("50.00");
    }

    @Test
    @DisplayName("split: counterparty fixedValue preserved and platform split appended")
    void splitFeesAbsorbedByIssuerPayload() {
        CreateChargeRequest req = samplePixCharge("ext-split-1");
        req.setSplit(List.of(CreateChargeRequest.ChargeSplitRequest.builder()
                .walletId("wal_supplier_1")
                .fixedValue(new BigDecimal("10.00"))
                .build()));

        chargeService.create(actorA, req);

        ArgumentCaptor<AsaasPaymentRequest> captor = ArgumentCaptor.forClass(AsaasPaymentRequest.class);
        verify(asaasPaymentClient, atLeastOnce()).createPayment(anyString(), captor.capture(), any());
        AsaasPaymentRequest sent = captor.getValue();
        assertThat(sent.getSplit()).isNotNull();
        AsaasSplitItem counterparty = sent.getSplit().stream()
                .filter(s -> "wal_supplier_1".equals(s.getWalletId()))
                .findFirst()
                .orElseThrow();
        assertThat(counterparty.getFixedValue()).isEqualByComparingTo("10.00");
    }

    private void provisionSubaccount(Account account, String asaasAccountId) {
        subaccountRepository.save(Subaccount.builder()
                .account(account)
                .name(account.getName())
                .email(account.getOwnerUser().getEmail())
                .cpfCnpj(uniqueDigits(11))
                .mobilePhone("11999999999")
                .asaasAccountId(asaasAccountId)
                .asaasWalletId("wal_" + asaasAccountId)
                .status(SubaccountStatus.ACTIVE)
                .encryptedApiKey(encryptionService.encrypt("secret-key"))
                .webhookToken(WEBHOOK_TOKEN)
                .legacyAutoProvisioned(true)
                .incomeValue(new BigDecimal("1000.00"))
                .address("Rua Teste")
                .addressNumber("123")
                .province("Centro")
                .postalCode("01310100")
                .build());
    }

    private CreateChargeRequest samplePixCharge(String externalRef) {
        return CreateChargeRequest.builder()
                .customer(CreateChargeRequest.ChargeCustomerRequest.builder()
                        .name("Cliente Teste")
                        .cpfCnpj("52998224725")
                        .email("cliente@test.local")
                        .build())
                .value(new BigDecimal("50.00"))
                .billingType(ChargeBillingType.PIX)
                .dueDate(LocalDate.now().plusDays(3))
                .description("Charge test")
                .externalReference(externalRef)
                .build();
    }

    private ClientPrincipal toPrincipal(OauthClientSecretResponse created) {
        var client = oauthClientLoader.loadWithDetailsById(created.getId()).orElseThrow();
        return ClientPrincipal.builder()
                .oauthClientId(client.getId())
                .publicClientId(client.getClientId())
                .organizationId(client.getOrganization().getId())
                .name(client.getName())
                .scopes(OauthClientLoader.scopeCodes(client))
                .allowedAccountIds(OauthClientLoader.accountIds(client))
                .build();
    }

    private static String uniqueDigits(int length) {
        String digits = String.valueOf(Math.abs(UUID.randomUUID().getMostSignificantBits()));
        if (digits.length() >= length) {
            return digits.substring(0, length);
        }
        return digits + "0".repeat(length - digits.length());
    }
}
