package com.theron.wallet.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.theron.wallet.BaseIntegrationTest;
import com.theron.wallet.TestFixtures;
import com.theron.wallet.dto.asaas.AsaasTransferResponse;
import com.theron.wallet.dto.asaas.AsaasWebhookPayload;
import com.theron.wallet.dto.request.AddOrganizationMemberRequest;
import com.theron.wallet.dto.request.CreateAccountRequest;
import com.theron.wallet.dto.request.CreateOrganizationRequest;
import com.theron.wallet.dto.request.CreatePixTransferRequest;
import com.theron.wallet.dto.request.CreateUserRequest;
import com.theron.wallet.dto.request.InternalTransferRequest;
import com.theron.wallet.dto.request.LoginRequest;
import com.theron.wallet.dto.request.UpdateUserRequest;
import com.theron.wallet.dto.response.AccountResponse;
import com.theron.wallet.dto.response.OrganizationResponse;
import com.theron.wallet.dto.response.PixTransferResponse;
import com.theron.wallet.dto.response.UserResponse;
import com.theron.wallet.entity.Account;
import com.theron.wallet.entity.AccountLimit;
import com.theron.wallet.entity.Notification;
import com.theron.wallet.entity.Subaccount;
import com.theron.wallet.entity.Transaction;
import com.theron.wallet.entity.Wallet;
import com.theron.wallet.enums.AccountType;
import com.theron.wallet.enums.DocumentType;
import com.theron.wallet.enums.NotificationType;
import com.theron.wallet.enums.PixKeyType;
import com.theron.wallet.enums.RoleCode;
import com.theron.wallet.enums.SubaccountStatus;
import com.theron.wallet.enums.TransactionStatus;
import com.theron.wallet.enums.TransactionType;
import com.theron.wallet.repository.AccountLimitRepository;
import com.theron.wallet.repository.AccountRepository;
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
import static org.hamcrest.Matchers.greaterThan;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class NotificationIntegrationTest extends BaseIntegrationTest {

    private static final String IDEMPOTENCY = "Idempotency-Key";
    private static final String PASSWORD = "SenhaForte1!";

    @Autowired private UserService userService;
    @Autowired private OrganizationService organizationService;
    @Autowired private OrganizationMembershipService membershipService;
    @Autowired private RoleAssignmentService roleAssignmentService;
    @Autowired private AccountService accountService;
    @Autowired private WalletService walletService;
    @Autowired private WebhookService webhookService;
    @Autowired private InternalTransferService internalTransferService;
    @Autowired private NotificationService notificationService;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private AccountLimitRepository accountLimitRepository;
    @Autowired private SubaccountRepository subaccountRepository;
    @Autowired private WalletRepository walletRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    private OrganizationResponse org;
    private OrganizationResponse orgB;
    private UserResponse owner;
    private UserResponse finance;
    private UserResponse ownerB;
    private AccountResponse account;
    private AccountResponse accountDest;
    private String tokenOwner;
    private String tokenFinance;
    private String tokenOwnerB;

    @BeforeEach
    void setUp() {
        org = createOrg("77889900000122");
        orgB = createOrg("77889900000123");
        owner = createUser("notif-owner@theron.test");
        finance = createUser("notif-fin@theron.test");
        ownerB = createUser("notif-owner-b@theron.test");

        membershipService.addMember(org.getId(), member(owner.getId()));
        membershipService.addMember(org.getId(), member(finance.getId()));
        membershipService.addMember(orgB.getId(), member(ownerB.getId()));
        roleAssignmentService.assignRolesInternal(org.getId(), owner.getId(), List.of(RoleCode.OWNER.name()));
        roleAssignmentService.assignRolesInternal(org.getId(), finance.getId(), List.of(RoleCode.FINANCE.name()));
        roleAssignmentService.assignRolesInternal(orgB.getId(), ownerB.getId(), List.of(RoleCode.OWNER.name()));
        tokenOwner = productAccessToken(owner.getEmail());
        tokenFinance = productAccessToken(finance.getEmail());
        tokenOwnerB = productAccessToken(ownerB.getEmail());

        account = accountService.create(org.getId(), CreateAccountRequest.builder()
                .name("Notif Account").type(AccountType.MAIN).build(), owner.getId(), "77889900101");
        accountDest = accountService.create(org.getId(), CreateAccountRequest.builder()
                .name("Notif Dest").type(AccountType.RESERVE).build(), finance.getId(), "77889900102");
        linkAsaas(account.getId(), "77889900101");
        linkAsaas(accountDest.getId(), "77889900102");
        configureLimits(account.getId(), "5000.00", "10000.00");
        fundWallet(account.getId(), "5000.00");

        when(asaasApiKeyResolver.resolveForSubaccount(any())).thenReturn("encrypted-resolved-key");
        stubAsaasTransfer("asaas_notif_default");
    }

    @Test
    @DisplayName("1. Criar — os 9 tipos aparecem")
    void allTypesAreCreated() throws Exception {
        login(owner.getEmail(), "device-new-1");
        changePassword(owner.getId());
        confirmDeposit("pay_notif_in");
        completePix("50.00", "notif-pix-sent", "asaas_pix_sent");
        // Legacy approval notification types (personal PIX no longer holds for ApprovalPolicy)
        seedLegacyApprovalNotifications();
        internalTransfer("notif-internal-1");

        for (NotificationType type : NotificationType.values()) {
            assertThat(notificationRepository.findByTypeWithDetails(type))
                    .as("expected type %s", type)
                    .isNotEmpty();
        }
    }

    @Test
    @DisplayName("2. Listar — GET 200 só do ator, createdAt DESC")
    void listOnlyActorNewestFirst() throws Exception {
        login(owner.getEmail(), "device-list-a");
        login(ownerB.getEmail(), "device-list-b");

        mockMvc.perform(get("/api/v1/notifications").header("Authorization", bearer(tokenOwner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()", greaterThan(0)))
                .andExpect(jsonPath("$.content[0].userId").value(owner.getId().toString()));

        String body = mockMvc.perform(get("/api/v1/notifications").header("Authorization", bearer(tokenOwnerB)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(body).doesNotContain(owner.getId().toString());
        assertThat(body).contains(ownerB.getId().toString());
    }

    @Test
    @DisplayName("3. Unread count — bate com readAt null")
    void unreadCountMatches() throws Exception {
        login(owner.getEmail(), "device-unread");
        long unread = notificationRepository.countByUser_IdAndReadAtIsNull(owner.getId());
        mockMvc.perform(get("/api/v1/notifications/unread-count").header("Authorization", bearer(tokenOwner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value((int) unread));
    }

    @Test
    @DisplayName("4. Mark read — POST /{id}/read decrementa o count")
    void markReadDecrementsCount() throws Exception {
        login(owner.getEmail(), "device-read-one");
        long before = notificationRepository.countByUser_IdAndReadAtIsNull(owner.getId());
        UUID id = notificationRepository.findByTypeWithDetails(NotificationType.LOGIN_NEW_DEVICE).stream()
                .filter(n -> n.getUser().getId().equals(owner.getId()))
                .findFirst()
                .orElseThrow()
                .getId();

        mockMvc.perform(post("/api/v1/notifications/{id}/read", id).header("Authorization", bearer(tokenOwner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readAt").isNotEmpty());

        mockMvc.perform(get("/api/v1/notifications/unread-count").header("Authorization", bearer(tokenOwner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value((int) (before - 1)));
    }

    @Test
    @DisplayName("5. Read all — unread-count = 0")
    void readAllClearsUnread() throws Exception {
        login(owner.getEmail(), "device-read-all");
        changePassword(owner.getId());

        mockMvc.perform(post("/api/v1/notifications/read-all").header("Authorization", bearer(tokenOwner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));
        mockMvc.perform(get("/api/v1/notifications/unread-count").header("Authorization", bearer(tokenOwner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));
    }

    @Test
    @DisplayName("6. Cross-tenant — ator B não lê notificação de A")
    void crossTenantForbidden() throws Exception {
        login(owner.getEmail(), "device-cross-a");
        Notification owners = notificationRepository.findByTypeWithDetails(NotificationType.LOGIN_NEW_DEVICE).stream()
                .filter(n -> n.getUser().getId().equals(owner.getId()))
                .findFirst()
                .orElseThrow();

        mockMvc.perform(post("/api/v1/notifications/{id}/read", owners.getId()).header("Authorization", bearer(tokenOwnerB)))
                .andExpect(status().isForbidden());

        String body = mockMvc.perform(get("/api/v1/notifications").header("Authorization", bearer(tokenOwnerB)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(body).doesNotContain(owners.getId().toString());
    }

    @Test
    @DisplayName("7. Duplicate event — replay não cria segunda notificação")
    void duplicateEventsIgnored() throws Exception {
        String payId = "pay_dup_in";
        confirmDeposit(payId);
        long received = notificationRepository.countByType(NotificationType.PIX_RECEIVED);
        confirmDepositWebhook(payId);
        assertThat(notificationRepository.countByType(NotificationType.PIX_RECEIVED)).isEqualTo(received);

        PixTransferResponse pix = completePix("50.00", "notif-dup-pix", "asaas_dup_pix");
        long sent = notificationRepository.countByType(NotificationType.PIX_SENT);
        webhookService.processTransferWebhook(transferDone(asaasIdOf(pix.getTransactionId())));
        assertThat(notificationRepository.countByType(NotificationType.PIX_SENT)).isEqualTo(sent);

        internalTransfer("notif-dup-internal");
        long transferSent = notificationRepository.countByType(NotificationType.TRANSFER_SENT);
        internalTransfer("notif-dup-internal");
        assertThat(notificationRepository.countByType(NotificationType.TRANSFER_SENT)).isEqualTo(transferSent);
    }

    private void login(String email, String deviceId) throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(LoginRequest.builder()
                                .email(email)
                                .password(PASSWORD)
                                .deviceId(deviceId)
                                .deviceName("JUnit")
                                .platform("test")
                                .build())))
                .andExpect(status().isOk());
    }

    private void changePassword(UUID userId) throws Exception {
        mockMvc.perform(patch("/api/v1/users/{id}", userId)
                        .header("Authorization", bearer(tokenOwner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(UpdateUserRequest.builder()
                                .password("SenhaForte2!")
                                .build())))
                .andExpect(status().isOk());
    }

    private void confirmDeposit(String paymentId) {
        Account acc = accountRepository.findByIdWithOrganization(account.getId()).orElseThrow();
        Wallet wallet = walletRepository.findByAccount_Id(account.getId()).orElseThrow();
        transactionRepository.saveAndFlush(Transaction.builder()
                .wallet(wallet)
                .organization(acc.getOrganization())
                .account(acc)
                .type(TransactionType.DEPOSIT)
                .status(TransactionStatus.PROCESSING)
                .amount(new BigDecimal("80.00"))
                .asaasPaymentId(paymentId)
                .idempotencyKey(UUID.randomUUID().toString())
                .build());
        confirmDepositWebhook(paymentId);
    }

    private void confirmDepositWebhook(String paymentId) {
        webhookService.processPaymentWebhook(AsaasWebhookPayload.builder()
                .event("PAYMENT_CONFIRMED")
                .payment(AsaasWebhookPayload.Payment.builder()
                        .id(paymentId)
                        .value(new BigDecimal("80.00"))
                        .status("RECEIVED")
                        .build())
                .build());
    }

    private PixTransferResponse completePix(String amount, String idempotencyKey, String asaasId) throws Exception {
        stubAsaasTransfer(asaasId);
        PixTransferResponse pix = postPix(amount, idempotencyKey);
        webhookService.processTransferWebhook(transferDone(asaasId));
        return pix;
    }

    private void seedLegacyApprovalNotifications() {
        UUID resourceId = UUID.randomUUID();
        notificationService.notify(
                owner.getId(), org.getId(), NotificationType.TRANSFER_APPROVAL_REQUIRED, resourceId, null);
        notificationService.notify(
                owner.getId(), org.getId(), NotificationType.TRANSFER_APPROVED, resourceId, null);
        notificationService.notify(
                owner.getId(), org.getId(), NotificationType.TRANSFER_REJECTED, resourceId, null);
    }

    private PixTransferResponse postPix(String amount, String idempotencyKey) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/pix/transfers")
                        .header("Authorization", bearer(tokenOwner))
                        .header(IDEMPOTENCY, idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CreatePixTransferRequest.builder()
                                .accountId(account.getId())
                                .amount(new BigDecimal(amount))
                                .destinationPixKey("12345678901")
                                .destinationPixKeyType(PixKeyType.CPF)
                                .description("notif pix")
                                .build())))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), PixTransferResponse.class);
    }

    private void internalTransfer(String idempotencyKey) {
        Subaccount sender = subaccountRepository.findByAccount_Id(account.getId()).orElseThrow();
        Subaccount receiver = subaccountRepository.findByAccount_Id(accountDest.getId()).orElseThrow();
        ensureSubaccountWallet(sender, new BigDecimal("400.00"));
        ensureSubaccountWallet(receiver, BigDecimal.ZERO);
        internalTransferService.transfer(owner.getId(), InternalTransferRequest.builder()
                .idempotencyKey(idempotencyKey)
                .senderSubaccountId(sender.getId())
                .receiverSubaccountId(receiver.getId())
                .amount(new BigDecimal("25.00"))
                .description("notif transfer")
                .build());
    }

    private void ensureSubaccountWallet(Subaccount subaccount, BigDecimal balance) {
        walletRepository.findBySubaccountId(subaccount.getId()).orElseGet(() ->
                walletRepository.saveAndFlush(Wallet.builder()
                        .subaccount(subaccount)
                        .balance(balance)
                        .currency("BRL")
                        .active(true)
                        .build()));
    }

    private String asaasIdOf(UUID transactionId) {
        return transactionRepository.findById(transactionId).orElseThrow().getAsaasPaymentId();
    }

    private AsaasWebhookPayload transferDone(String transferId) {
        return AsaasWebhookPayload.builder()
                .event("TRANSFER_DONE")
                .transfer(AsaasWebhookPayload.Transfer.builder()
                        .id(transferId)
                        .value(new BigDecimal("50.00"))
                        .status("DONE")
                        .build())
                .build();
    }

    private void stubAsaasTransfer(String id) {
        when(asaasTransferClient.createTransfer(any(), any())).thenReturn(
                AsaasTransferResponse.builder().id(id).status("PENDING").build());
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
        BigDecimal delta = target.subtract(wallet.getBalance());
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

    private UserResponse createUser(String email) {
        return userService.create(CreateUserRequest.builder()
                .name(email)
                .email(email)
                .password(PASSWORD)
                .build());
    }

    private static AddOrganizationMemberRequest member(UUID userId) {
        return AddOrganizationMemberRequest.builder().userId(userId).build();
    }
}
