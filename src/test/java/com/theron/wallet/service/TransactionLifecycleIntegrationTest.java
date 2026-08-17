package com.theron.wallet.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.theron.wallet.BaseIntegrationTest;
import com.theron.wallet.TestFixtures;
import com.theron.wallet.dto.asaas.AsaasPaymentResponse;
import com.theron.wallet.dto.asaas.AsaasTransferResponse;
import com.theron.wallet.dto.asaas.AsaasWebhookPayload;
import com.theron.wallet.dto.request.CreateUserRequest;
import com.theron.wallet.dto.request.DepositRequest;
import com.theron.wallet.dto.request.WithdrawRequest;
import com.theron.wallet.dto.response.DepositResponse;
import com.theron.wallet.dto.response.WithdrawResponse;
import com.theron.wallet.entity.Subaccount;
import com.theron.wallet.entity.Transaction;
import com.theron.wallet.entity.Wallet;
import com.theron.wallet.enums.SubaccountStatus;
import com.theron.wallet.enums.TransactionStatus;
import com.theron.wallet.enums.TransactionType;
import com.theron.wallet.exception.AsaasApiException;
import com.theron.wallet.exception.InvalidRequestException;
import com.theron.wallet.repository.SubaccountRepository;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.ResourceAccessException;

import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class TransactionLifecycleIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private DepositService depositService;

    @Autowired
    private WithdrawService withdrawService;

    @Autowired
    private UserService userService;

    @Autowired
    private WebhookService webhookService;

    @Autowired
    private TransactionLifecycleService transactionLifecycleService;

    @Autowired
    private SubaccountRepository subaccountRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private Subaccount subaccount;
    private Wallet wallet;
    private UUID actorUserId;

    @BeforeEach
    void setUp() {
        subaccount = TestFixtures.aSubaccount(SubaccountStatus.ACTIVE);
        subaccount.setAsaasCustomerId("cus_test_" + UUID.randomUUID().toString().substring(0, 12));
        subaccount = subaccountRepository.save(subaccount);
        wallet = walletRepository.save(TestFixtures.aWalletWithBalance(subaccount, new BigDecimal("500.00")));
        actorUserId = userService.create(CreateUserRequest.builder()
                .name("Lifecycle Actor")
                .email("lifecycle-actor@theron.test")
                .password("SenhaForte1!")
                .build()).getId();
        when(asaasApiKeyResolver.resolveForSubaccount(any())).thenReturn("root-api-key");
        when(asaasPaymentClient.createPayment(anyString(), any()))
                .thenReturn(AsaasPaymentResponse.builder().id("pay_lifecycle").status("PENDING").build());
        when(asaasTransferClient.createTransfer(anyString(), any()))
                .thenReturn(AsaasTransferResponse.builder().id("transfer_lifecycle").status("PENDING").build());
    }

    @Test
    @DisplayName("1. criar depósito com Idempotency-Key retorna PROCESSING")
    void createWithIdempotencyHeader() throws Exception {
        String key = UUID.randomUUID().toString();
        MvcResult result = mockMvc.perform(post("/api/v1/deposits")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(DepositRequest.builder()
                                .subaccountId(subaccount.getId())
                                .amount(new BigDecimal("40.00"))
                                .description("Lifecycle create")
                                .build())))
                .andExpect(status().isCreated())
                .andReturn();

        DepositResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), DepositResponse.class);
        assertThat(response.getStatus()).isEqualTo("PROCESSING");
        Transaction persisted = transactionRepository.findById(response.getTransactionId()).orElseThrow();
        assertThat(persisted.getIdempotencyKey()).isEqualTo(key);
        assertThat(persisted.getCurrency()).isEqualTo("BRL");
        assertThat(persisted.getRequestHash()).isNotBlank();
        assertThat(persisted.getStatus()).isEqualTo(TransactionStatus.PROCESSING);
    }

    @Test
    @DisplayName("2. duplicate request devolve a mesma operação")
    void duplicateRequestReturnsSameOperation() {
        String key = UUID.randomUUID().toString();
        DepositRequest request = DepositRequest.builder()
                .subaccountId(subaccount.getId())
                .amount(new BigDecimal("15.00"))
                .idempotencyKey(key)
                .build();

        DepositResponse first = depositService.createPixDeposit(request);
        DepositResponse second = depositService.createPixDeposit(request);

        assertThat(second.getTransactionId()).isEqualTo(first.getTransactionId());
        assertThat(transactionRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("3. duplicate concorrente persiste uma única row")
    void concurrentDuplicatePersistsOnce() throws Exception {
        String key = UUID.randomUUID().toString();
        DepositRequest request = DepositRequest.builder()
                .subaccountId(subaccount.getId())
                .amount(new BigDecimal("22.00"))
                .idempotencyKey(key)
                .build();

        int threads = 2;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<DepositResponse>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                try {
                    start.await();
                    return depositService.createPixDeposit(request);
                } finally {
                    done.countDown();
                }
            }));
        }
        start.countDown();
        done.await();
        pool.shutdown();

        UUID firstId = futures.getFirst().get().getTransactionId();
        assertThat(futures.get(1).get().getTransactionId()).isEqualTo(firstId);
        assertThat(transactionRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("4. same key / same payload devolve a mesma operação")
    void sameKeySamePayload() throws Exception {
        String key = UUID.randomUUID().toString();
        DepositRequest body = DepositRequest.builder()
                .subaccountId(subaccount.getId())
                .amount(new BigDecimal("18.00"))
                .build();

        MvcResult first = mockMvc.perform(post("/api/v1/deposits")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();
        MvcResult second = mockMvc.perform(post("/api/v1/deposits")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();

        DepositResponse a = objectMapper.readValue(first.getResponse().getContentAsString(), DepositResponse.class);
        DepositResponse b = objectMapper.readValue(second.getResponse().getContentAsString(), DepositResponse.class);
        assertThat(b.getTransactionId()).isEqualTo(a.getTransactionId());
        assertThat(transactionRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("5. same key / different payload retorna 409 e não altera a tx original")
    void sameKeyDifferentPayloadConflict() throws Exception {
        String key = UUID.randomUUID().toString();
        mockMvc.perform(post("/api/v1/deposits")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(DepositRequest.builder()
                                .subaccountId(subaccount.getId())
                                .amount(new BigDecimal("10.00"))
                                .build())))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/deposits")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(DepositRequest.builder()
                                .subaccountId(subaccount.getId())
                                .amount(new BigDecimal("99.00"))
                                .build())))
                .andExpect(status().isConflict());

        assertThat(transactionRepository.findAll()).hasSize(1);
        assertThat(transactionRepository.findAll().getFirst().getAmount())
                .isEqualByComparingTo(new BigDecimal("10.00"));
    }

    @Test
    @DisplayName("6. falha do provider marca FAILED e restaura saldo no saque")
    void failedMarksFailedAndRestoresBalance() {
        when(asaasTransferClient.createTransfer(anyString(), any()))
                .thenThrow(new AsaasApiException("Asaas error", 500, "fail"));

        assertThatThrownBy(() -> withdrawService.createWithdraw(actorUserId, WithdrawRequest.builder()
                .subaccountId(subaccount.getId())
                .amount(new BigDecimal("50.00"))
                .pixAddressKey("12345678901")
                .pixAddressKeyType("CPF")
                .idempotencyKey(UUID.randomUUID().toString())
                .build()))
                .isInstanceOf(AsaasApiException.class);

        Transaction failed = transactionRepository.findAll().getFirst();
        assertThat(failed.getStatus()).isEqualTo(TransactionStatus.FAILED);
        assertThat(walletRepository.findById(wallet.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo(new BigDecimal("500.00"));
    }

    @Test
    @DisplayName("7. retry com a mesma key após FAILED devolve a mesma tx sem duplicar")
    void retryAfterFailedReturnsSameTransaction() {
        String key = UUID.randomUUID().toString();
        when(asaasTransferClient.createTransfer(anyString(), any()))
                .thenThrow(new AsaasApiException("Asaas error", 500, "fail"));

        WithdrawRequest request = WithdrawRequest.builder()
                .subaccountId(subaccount.getId())
                .amount(new BigDecimal("30.00"))
                .pixAddressKey("12345678901")
                .pixAddressKeyType("CPF")
                .idempotencyKey(key)
                .build();

        assertThatThrownBy(() -> withdrawService.createWithdraw(actorUserId, request))
                .isInstanceOf(AsaasApiException.class);

        WithdrawResponse retry = withdrawService.createWithdraw(actorUserId, request);
        assertThat(retry.getStatus()).isEqualTo("FAILED");
        assertThat(transactionRepository.findAll()).hasSize(1);
        verify(asaasTransferClient, times(1)).createTransfer(anyString(), any());
    }

    @Test
    @DisplayName("8. transições PENDING → PROCESSING → COMPLETED → REVERSED")
    void allowedStatusTransitions() {
        Transaction transaction = transactionRepository.save(Transaction.builder()
                .wallet(wallet)
                .type(TransactionType.DEPOSIT)
                .status(TransactionStatus.PENDING)
                .amount(new BigDecimal("80.00"))
                .idempotencyKey(UUID.randomUUID().toString())
                .asaasPaymentId("pay_transition")
                .build());

        transaction = transactionLifecycleService.transition(transaction, TransactionStatus.PROCESSING);
        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.PROCESSING);

        transaction = transactionLifecycleService.transition(transaction, TransactionStatus.COMPLETED);
        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(transaction.getCompletedAt()).isNotNull();

        webhookService.processPaymentWebhook(AsaasWebhookPayload.builder()
                .event("PAYMENT_REFUNDED")
                .payment(AsaasWebhookPayload.Payment.builder()
                        .id("pay_transition")
                        .value(new BigDecimal("80.00"))
                        .build())
                .build());

        Transaction reversed = transactionRepository.findById(transaction.getId()).orElseThrow();
        assertThat(reversed.getStatus()).isEqualTo(TransactionStatus.REVERSED);
    }

    @Test
    @DisplayName("9. transição inválida lança exceção e não altera o status")
    void invalidTransitionRejected() {
        Transaction completed = transactionRepository.save(Transaction.builder()
                .wallet(wallet)
                .type(TransactionType.DEPOSIT)
                .status(TransactionStatus.COMPLETED)
                .amount(new BigDecimal("5.00"))
                .idempotencyKey(UUID.randomUUID().toString())
                .build());

        assertThatThrownBy(() -> transactionLifecycleService.transition(completed, TransactionStatus.PENDING))
                .isInstanceOf(InvalidRequestException.class);
        assertThat(transactionRepository.findById(completed.getId()).orElseThrow().getStatus())
                .isEqualTo(TransactionStatus.COMPLETED);

        Transaction failed = transactionRepository.save(Transaction.builder()
                .wallet(wallet)
                .type(TransactionType.WITHDRAWAL)
                .status(TransactionStatus.FAILED)
                .amount(new BigDecimal("5.00"))
                .idempotencyKey(UUID.randomUUID().toString())
                .build());
        assertThatThrownBy(() -> transactionLifecycleService.transition(failed, TransactionStatus.COMPLETED))
                .isInstanceOf(InvalidRequestException.class);
        assertThat(transactionRepository.findById(failed.getId()).orElseThrow().getStatus())
                .isEqualTo(TransactionStatus.FAILED);
    }

    @Test
    @DisplayName("10. rollback no meio da TX de persistência não grava rows")
    void rollbackDuringPersistWritesNothing() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        long before = transactionRepository.count();

        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            transactionRepository.save(Transaction.builder()
                    .wallet(wallet)
                    .type(TransactionType.DEPOSIT)
                    .status(TransactionStatus.PENDING)
                    .amount(new BigDecimal("12.00"))
                    .idempotencyKey(UUID.randomUUID().toString())
                    .build());
            transactionRepository.flush();
            throw new IllegalStateException("forced rollback");
        })).isInstanceOf(IllegalStateException.class)
                .hasMessage("forced rollback");

        assertThat(transactionRepository.count()).isEqualTo(before);
    }

    @Test
    @DisplayName("11. timeout do provider deixa PROCESSING e não duplica")
    void providerTimeoutLeavesProcessing() {
        when(asaasPaymentClient.createPayment(anyString(), any()))
                .thenThrow(new ResourceAccessException("timeout", new SocketTimeoutException("timeout")));

        DepositRequest request = DepositRequest.builder()
                .subaccountId(subaccount.getId())
                .amount(new BigDecimal("9.00"))
                .idempotencyKey(UUID.randomUUID().toString())
                .build();

        assertThatThrownBy(() -> depositService.createPixDeposit(request))
                .isInstanceOf(ResourceAccessException.class);

        assertThat(transactionRepository.findAll()).hasSize(1);
        Transaction persisted = transactionRepository.findAll().getFirst();
        assertThat(persisted.getStatus()).isEqualTo(TransactionStatus.PROCESSING);
        assertThat(persisted.getAsaasPaymentId()).isNull();
    }

    @Test
    @DisplayName("12. retry após timeout reutiliza a mesma tx e completa o provider")
    void providerRetryAfterTimeout() {
        String key = UUID.randomUUID().toString();
        when(asaasPaymentClient.createPayment(anyString(), any()))
                .thenThrow(new ResourceAccessException("timeout", new SocketTimeoutException("timeout")))
                .thenReturn(AsaasPaymentResponse.builder().id("pay_retry").status("PENDING").build());

        DepositRequest request = DepositRequest.builder()
                .subaccountId(subaccount.getId())
                .amount(new BigDecimal("11.00"))
                .idempotencyKey(key)
                .build();

        assertThatThrownBy(() -> depositService.createPixDeposit(request))
                .isInstanceOf(ResourceAccessException.class);

        DepositResponse retry = depositService.createPixDeposit(request);
        assertThat(retry.getAsaasPaymentId()).isEqualTo("pay_retry");
        assertThat(retry.getStatus()).isEqualTo("PROCESSING");
        assertThat(transactionRepository.findAll()).hasSize(1);
        assertThat(transactionRepository.findAll().getFirst().getId()).isEqualTo(retry.getTransactionId());
        verify(asaasPaymentClient, times(2)).createPayment(anyString(), any());
    }
}
