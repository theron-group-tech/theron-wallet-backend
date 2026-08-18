package com.theron.wallet.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.theron.wallet.BaseIntegrationTest;
import com.theron.wallet.dto.request.AddOrganizationMemberRequest;
import com.theron.wallet.dto.request.CreateAccountRequest;
import com.theron.wallet.dto.request.CreateOrganizationRequest;
import com.theron.wallet.dto.request.CreateUserRequest;
import com.theron.wallet.dto.request.LoginRequest;
import com.theron.wallet.dto.request.RefreshTokenRequest;
import com.theron.wallet.dto.response.AccountResponse;
import com.theron.wallet.dto.response.LoginResponse;
import com.theron.wallet.dto.response.OrganizationResponse;
import com.theron.wallet.dto.response.UserResponse;
import com.theron.wallet.entity.Account;
import com.theron.wallet.entity.Transaction;
import com.theron.wallet.entity.Wallet;
import com.theron.wallet.enums.AccountType;
import com.theron.wallet.enums.DocumentType;
import com.theron.wallet.enums.TransactionStatus;
import com.theron.wallet.enums.TransactionType;
import com.theron.wallet.repository.AccountRepository;
import com.theron.wallet.repository.TransactionRepository;
import com.theron.wallet.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class MobileReadyApiIntegrationTest extends BaseIntegrationTest {

    private static final String PASSWORD = "SenhaForte1!";
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserService userService;
    @Autowired private OrganizationService organizationService;
    @Autowired private OrganizationMembershipService membershipService;
    @Autowired private AccountService accountService;
    @Autowired private WalletService walletService;
    @Autowired private AccountRepository accountRepository;
    @Autowired private WalletRepository walletRepository;
    @Autowired private TransactionRepository transactionRepository;

    private UserResponse userA;
    private UserResponse userB;
    private UserResponse userEmpty;
    private OrganizationResponse orgA;
    private OrganizationResponse orgB;
    private AccountResponse accountA;
    private AccountResponse accountB;
    private AccountResponse accountEmpty;
    private String tokenA;
    private String refreshA;
    private String tokenB;
    private String tokenEmpty;

    @BeforeEach
    void setUp() throws Exception {
        orgA = createOrg("55111222000101");
        orgB = createOrg("55111222000102");
        userA = createUser("mobile-a@theron.test");
        userB = createUser("mobile-b@theron.test");
        userEmpty = createUser("mobile-empty@theron.test");

        membershipService.addMember(orgA.getId(), member(userA.getId()));
        membershipService.addMember(orgB.getId(), member(userB.getId()));
        membershipService.addMember(orgA.getId(), member(userEmpty.getId()));

        accountA = accountService.create(orgA.getId(), CreateAccountRequest.builder()
                .name("Mobile A").type(AccountType.MAIN).build());
        accountB = accountService.create(orgB.getId(), CreateAccountRequest.builder()
                .name("Mobile B").type(AccountType.MAIN).build());
        accountEmpty = accountService.create(orgA.getId(), CreateAccountRequest.builder()
                .name("Mobile Empty").type(AccountType.RESERVE).build());

        fund(accountA.getId(), "200.00");
        fund(accountB.getId(), "10.00");

        LoginResponse loginA = login(userA.getEmail(), "device-a");
        tokenA = loginA.getAccessToken();
        refreshA = loginA.getRefreshToken();
        tokenB = login(userB.getEmail(), "device-b").getAccessToken();
        tokenEmpty = login(userEmpty.getEmail(), "device-empty").getAccessToken();
    }

    @Test
    @DisplayName("1 Dashboard — income, blocked and available")
    void dashboard() throws Exception {
        saveTx(accountA.getId(), TransactionType.DEPOSIT, TransactionStatus.COMPLETED,
                "100.00", LocalDateTime.now());
        saveTx(accountA.getId(), TransactionType.PIX, TransactionStatus.PENDING_APPROVAL,
                "30.00", null);

        mockMvc.perform(get("/api/v1/me/dashboard")
                        .header("Authorization", bearer(tokenA))
                        .param("accountId", accountA.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("BRL"))
                .andExpect(jsonPath("$.balance").value(200.00))
                .andExpect(jsonPath("$.blockedBalance").value(30.00))
                .andExpect(jsonPath("$.availableBalance").value(170.00))
                .andExpect(jsonPath("$.todayIncome").value(100.00))
                .andExpect(jsonPath("$.todayExpenses").value(0.00))
                .andExpect(jsonPath("$.pendingTransactions", hasSize(1)))
                .andExpect(jsonPath("$.accountId").value(accountA.getId().toString()));
    }

    @Test
    @DisplayName("2 Statement — lists seeded transactions")
    void statement() throws Exception {
        Transaction deposit = saveTx(accountA.getId(), TransactionType.DEPOSIT, TransactionStatus.COMPLETED,
                "40.00", LocalDateTime.now());
        Transaction pix = saveTx(accountA.getId(), TransactionType.PIX, TransactionStatus.PROCESSING,
                "15.00", null);

        mockMvc.perform(get("/api/v1/accounts/{accountId}/statement", accountA.getId())
                        .header("Authorization", bearer(tokenA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[*].id").value(org.hamcrest.Matchers.containsInAnyOrder(
                        deposit.getId().toString(), pix.getId().toString())))
                .andExpect(jsonPath("$.content[0].asaasPaymentId").doesNotExist());
    }

    @Test
    @DisplayName("3 Pagination — 25 txs, size=10 → 3 pages")
    void pagination() throws Exception {
        seedTransactions(accountA.getId(), 25, TransactionType.DEPOSIT, TransactionStatus.COMPLETED, "1.00");

        mockMvc.perform(get("/api/v1/accounts/{accountId}/statement", accountA.getId())
                        .header("Authorization", bearer(tokenA))
                        .param("size", "10")
                        .param("page", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.totalElements").value(25))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(false))
                .andExpect(jsonPath("$.content", hasSize(10)));

        mockMvc.perform(get("/api/v1/accounts/{accountId}/statement", accountA.getId())
                        .header("Authorization", bearer(tokenA))
                        .param("size", "10")
                        .param("page", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.last").value(true))
                .andExpect(jsonPath("$.content", hasSize(5)));
    }

    @Test
    @DisplayName("4 Filters — type, status, minAmount, from, to")
    void filters() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        saveTx(accountA.getId(), TransactionType.PIX, TransactionStatus.COMPLETED, "50.00", now);
        saveTx(accountA.getId(), TransactionType.PIX, TransactionStatus.FAILED, "50.00", now);
        saveTx(accountA.getId(), TransactionType.DEPOSIT, TransactionStatus.COMPLETED, "50.00", now);
        saveTx(accountA.getId(), TransactionType.PIX, TransactionStatus.COMPLETED, "10.00", now);

        mockMvc.perform(get("/api/v1/me/transactions")
                        .header("Authorization", bearer(tokenA))
                        .param("accountId", accountA.getId().toString())
                        .param("type", "PIX")
                        .param("status", "COMPLETED")
                        .param("minAmount", "40")
                        .param("from", now.minusMinutes(5).format(ISO))
                        .param("to", now.plusMinutes(5).format(ISO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].type").value("PIX"))
                .andExpect(jsonPath("$.content[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.content[0].amount").value(50.00));
    }

    @Test
    @DisplayName("5 Unauthorized — missing and invalid Bearer")
    void unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/api/v1/me"));

        mockMvc.perform(get("/api/v1/me")
                        .header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    @DisplayName("6 Cross-tenant — user B cannot read account A")
    void crossTenant() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{accountId}/statement", accountA.getId())
                        .header("Authorization", bearer(tokenB)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(get("/api/v1/me/dashboard")
                        .header("Authorization", bearer(tokenB))
                        .param("accountId", accountA.getId().toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("7 Empty results — zeros and empty pages")
    void emptyResults() throws Exception {
        mockMvc.perform(get("/api/v1/me/dashboard")
                        .header("Authorization", bearer(tokenEmpty))
                        .param("accountId", accountEmpty.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(0.00))
                .andExpect(jsonPath("$.blockedBalance").value(0.00))
                .andExpect(jsonPath("$.availableBalance").value(0.00))
                .andExpect(jsonPath("$.todayIncome").value(0.00))
                .andExpect(jsonPath("$.todayExpenses").value(0.00))
                .andExpect(jsonPath("$.pendingTransactions", hasSize(0)))
                .andExpect(jsonPath("$.recentTransactions", hasSize(0)));

        mockMvc.perform(get("/api/v1/accounts/{accountId}/statement", accountEmpty.getId())
                        .header("Authorization", bearer(tokenEmpty)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.empty").doesNotExist());
    }

    @Test
    @DisplayName("8 Large result set — size cap 100")
    void largeResultSet() throws Exception {
        seedTransactions(accountA.getId(), 120, TransactionType.DEPOSIT, TransactionStatus.COMPLETED, "1.00");

        mockMvc.perform(get("/api/v1/accounts/{accountId}/statement", accountA.getId())
                        .header("Authorization", bearer(tokenA))
                        .param("size", "100")
                        .param("page", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100))
                .andExpect(jsonPath("$.totalElements").value(120))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content", hasSize(100)));

        mockMvc.perform(get("/api/v1/accounts/{accountId}/statement", accountA.getId())
                        .header("Authorization", bearer(tokenA))
                        .param("size", "200")
                        .param("page", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100))
                .andExpect(jsonPath("$.content", hasSize(100)));

        mockMvc.perform(get("/api/v1/accounts/{accountId}/statement", accountA.getId())
                        .header("Authorization", bearer(tokenA))
                        .param("size", "100")
                        .param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.content", hasSize(20)));
    }

    @Test
    @DisplayName("9 Error format — 401/404/422 with code and traceId")
    void errorFormat() throws Exception {
        mockMvc.perform(get("/api/v1/me/dashboard"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.path").value("/api/v1/me/dashboard"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());

        mockMvc.perform(get("/api/v1/accounts/{accountId}/statement", UUID.randomUUID())
                        .header("Authorization", bearer(tokenA)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());

        mockMvc.perform(get("/api/v1/accounts/{accountId}/statement", accountA.getId())
                        .header("Authorization", bearer(tokenA))
                        .param("from", "2026-08-18T12:00:00")
                        .param("to", "2026-08-18T10:00:00"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    @DisplayName("10 Mobile client — login, me, lists, refresh")
    void mobileClientFlow() throws Exception {
        mockMvc.perform(get("/api/v1/me").header("Authorization", bearer(tokenA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userA.getId().toString()))
                .andExpect(jsonPath("$.email").value(userA.getEmail()))
                .andExpect(jsonPath("$.organizations", hasSize(1)))
                .andExpect(jsonPath("$.password").doesNotExist());

        mockMvc.perform(get("/api/v1/me/dashboard").header("Authorization", bearer(tokenA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("BRL"));

        mockMvc.perform(get("/api/v1/me/accounts").header("Authorization", bearer(tokenA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)));

        mockMvc.perform(get("/api/v1/me/wallets").header("Authorization", bearer(tokenA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)));

        mockMvc.perform(get("/api/v1/me/transactions").header("Authorization", bearer(tokenA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());

        mockMvc.perform(get("/api/v1/me/notifications").header("Authorization", bearer(tokenA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)));

        MvcResult refreshed = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                RefreshTokenRequest.builder().refreshToken(refreshA).build())))
                .andExpect(status().isOk())
                .andReturn();
        LoginResponse pair = objectMapper.readValue(refreshed.getResponse().getContentAsString(), LoginResponse.class);

        mockMvc.perform(get("/api/v1/me").header("Authorization", bearer(pair.getAccessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userA.getId().toString()));
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

    private void fund(UUID accountId, String amount) {
        Wallet wallet = walletRepository.findByAccount_Id(accountId).orElseThrow();
        BigDecimal target = new BigDecimal(amount);
        if (target.compareTo(BigDecimal.ZERO) > 0) {
            walletService.credit(wallet.getId(), target);
        }
    }

    private Transaction saveTx(
            UUID accountId,
            TransactionType type,
            TransactionStatus status,
            String amount,
            LocalDateTime completedAt) {
        Account account = accountRepository.findByIdWithOrganization(accountId).orElseThrow();
        Wallet wallet = walletRepository.findByAccount_Id(accountId).orElseThrow();
        Transaction tx = Transaction.builder()
                .wallet(wallet)
                .account(account)
                .organization(account.getOrganization())
                .type(type)
                .status(status)
                .amount(new BigDecimal(amount))
                .currency("BRL")
                .description("mobile-test")
                .idempotencyKey(UUID.randomUUID().toString())
                .completedAt(completedAt)
                .build();
        return transactionRepository.saveAndFlush(tx);
    }

    private void seedTransactions(
            UUID accountId, int count, TransactionType type, TransactionStatus status, String amount) {
        Account account = accountRepository.findByIdWithOrganization(accountId).orElseThrow();
        Wallet wallet = walletRepository.findByAccount_Id(accountId).orElseThrow();
        List<Transaction> batch = new ArrayList<>(count);
        LocalDateTime completedAt = status == TransactionStatus.COMPLETED ? LocalDateTime.now() : null;
        for (int i = 0; i < count; i++) {
            batch.add(Transaction.builder()
                    .wallet(wallet)
                    .account(account)
                    .organization(account.getOrganization())
                    .type(type)
                    .status(status)
                    .amount(new BigDecimal(amount))
                    .currency("BRL")
                    .description("seed-" + i)
                    .idempotencyKey(UUID.randomUUID().toString())
                    .completedAt(completedAt)
                    .build());
        }
        transactionRepository.saveAll(batch);
        transactionRepository.flush();
    }

    private LoginResponse login(String email, String deviceId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("User-Agent", "JUnit-Mobile")
                        .content(objectMapper.writeValueAsString(LoginRequest.builder()
                                .email(email)
                                .password(PASSWORD)
                                .deviceId(deviceId)
                                .deviceName("JUnit " + deviceId)
                                .platform("test")
                                .build())))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), LoginResponse.class);
    }
}
