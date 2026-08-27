package com.theron.wallet.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theron.wallet.BaseIntegrationTest;
import com.theron.wallet.TestFixtures;
import com.theron.wallet.config.AsaasProperties;
import com.theron.wallet.controller.WebhookController;
import com.theron.wallet.dto.asaas.AsaasPaymentResponse;
import com.theron.wallet.dto.asaas.AsaasWebhookPayload;
import com.theron.wallet.dto.response.SubaccountResponse;
import com.theron.wallet.entity.AsaasReconciliation;
import com.theron.wallet.entity.Subaccount;
import com.theron.wallet.entity.Transaction;
import com.theron.wallet.entity.Wallet;
import com.theron.wallet.enums.ReconciliationKind;
import com.theron.wallet.enums.ReconciliationStatus;
import com.theron.wallet.enums.SubaccountStatus;
import com.theron.wallet.enums.TransactionStatus;
import com.theron.wallet.exception.ApiErrorResponse;
import com.theron.wallet.exception.AsaasApiException;
import com.theron.wallet.exception.GlobalExceptionHandler;
import com.theron.wallet.integration.AsaasHttpGateway;
import com.theron.wallet.mapper.SubaccountMapper;
import com.theron.wallet.repository.SubaccountRepository;
import com.theron.wallet.repository.TransactionRepository;
import com.theron.wallet.repository.WalletRepository;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AsaasHardeningIntegrationTest extends BaseIntegrationTest {

    private static final String WEBHOOK_TOKEN = "test-webhook-token";
    private static final String FAKE_API_KEY = "$aact_LiveSecretKeyForHardeningTest";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ReconciliationService reconciliationService;
    @Autowired
    private SubaccountRepository subaccountRepository;
    @Autowired
    private WalletRepository walletRepository;
    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private ApplicationContext applicationContext;

    @Nested
    @DisplayName("HTTP gateway")
    class HttpGatewayTests {

        private MockWebServer server;

        @BeforeEach
        void startServer() throws Exception {
            server = new MockWebServer();
            server.start();
        }

        @AfterEach
        void stopServer() throws Exception {
            server.shutdown();
        }

        @Test
        @DisplayName("1 Success — GET/POST 200 mapeia response")
        void successMapsResponse() {
            AsaasHttpGateway gateway = gateway(10_000);
            enqueueJson(200, "{\"id\":\"pay_1\",\"status\":\"PENDING\",\"value\":10.00}");
            enqueueJson(200, "{\"id\":\"pay_2\",\"status\":\"CONFIRMED\",\"value\":20.00}");

            AsaasPaymentResponse get = gateway.get(FAKE_API_KEY, "/payments/{id}", AsaasPaymentResponse.class, "pay_1");
            AsaasPaymentResponse post = gateway.postFinancial(
                    FAKE_API_KEY, "/payments", Map.of("value", 20), "idem-pay-2", AsaasPaymentResponse.class);

            assertThat(get.getId()).isEqualTo("pay_1");
            assertThat(get.getStatus()).isEqualTo("PENDING");
            assertThat(post.getId()).isEqualTo("pay_2");
            assertThat(post.getValue()).isEqualByComparingTo("20.00");
        }

        @Test
        @DisplayName("2 400 → AsaasApiException 400")
        void mapsBadRequest() {
            AsaasHttpGateway gateway = gateway(10_000);
            enqueueJson(400, "{\"errors\":[{\"description\":\"invalid value\"}]}");

            assertThatThrownBy(() -> gateway.get(FAKE_API_KEY, "/payments/x", AsaasPaymentResponse.class))
                    .isInstanceOf(AsaasApiException.class)
                    .satisfies(ex -> {
                        AsaasApiException asaas = (AsaasApiException) ex;
                        assertThat(asaas.getAsaasStatusCode()).isEqualTo(400);
                        assertThat(asaas.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    });
        }

        @Test
        @DisplayName("3 401 → 502")
        void mapsUnauthorizedToBadGateway() {
            AsaasHttpGateway gateway = gateway(10_000);
            enqueueJson(401, "{\"errors\":[{\"description\":\"invalid access_token\"}]}");

            assertThatThrownBy(() -> gateway.get(FAKE_API_KEY, "/payments/x", AsaasPaymentResponse.class))
                    .isInstanceOf(AsaasApiException.class)
                    .satisfies(ex -> {
                        AsaasApiException asaas = (AsaasApiException) ex;
                        assertThat(asaas.getAsaasStatusCode()).isEqualTo(401);
                        assertThat(asaas.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    });
        }

        @Test
        @DisplayName("4 429 → após retries, 429")
        void rateLimitAfterRetries() throws Exception {
            AsaasHttpGateway gateway = gateway(10_000);
            enqueueJson(429, "{\"errors\":[{\"description\":\"rate limit\"}]}", "0");
            enqueueJson(429, "{\"errors\":[{\"description\":\"rate limit\"}]}", "0");
            enqueueJson(429, "{\"errors\":[{\"description\":\"rate limit\"}]}", "0");

            assertThatThrownBy(() -> gateway.get(FAKE_API_KEY, "/payments/x", AsaasPaymentResponse.class))
                    .isInstanceOf(AsaasApiException.class)
                    .satisfies(ex -> {
                        AsaasApiException asaas = (AsaasApiException) ex;
                        assertThat(asaas.getAsaasStatusCode()).isEqualTo(429);
                        assertThat(asaas.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    });
            assertThat(server.getRequestCount()).isEqualTo(3);
        }

        @Test
        @DisplayName("5 500 → 502; POST financeiro uma tentativa")
        void financialPostDoesNotRetryServerError() {
            AsaasHttpGateway gateway = gateway(10_000);
            enqueueJson(500, "{\"errors\":[{\"description\":\"boom\"}]}");
            enqueueJson(500, "{\"errors\":[{\"description\":\"boom\"}]}");

            assertThatThrownBy(() -> gateway.postFinancial(
                    FAKE_API_KEY, "/payments", Map.of("value", 1), "idem-500", AsaasPaymentResponse.class))
                    .isInstanceOf(AsaasApiException.class)
                    .satisfies(ex -> {
                        AsaasApiException asaas = (AsaasApiException) ex;
                        assertThat(asaas.getAsaasStatusCode()).isEqualTo(500);
                        assertThat(asaas.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    });
            assertThat(server.getRequestCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("6 Timeout → 504; POST financeiro sem retry")
        void financialPostDoesNotRetryTimeout() {
            AsaasHttpGateway gateway = gateway(300);
            server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));
            server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));

            assertThatThrownBy(() -> gateway.postFinancial(
                    FAKE_API_KEY, "/payments", Map.of("value", 1), "idem-timeout", AsaasPaymentResponse.class))
                    .isInstanceOf(AsaasApiException.class)
                    .satisfies(ex -> {
                        AsaasApiException asaas = (AsaasApiException) ex;
                        assertThat(asaas.getAsaasStatusCode()).isEqualTo(504);
                        assertThat(asaas.getStatus()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
                    });
            assertThat(server.getRequestCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("7 Retry — 429 depois 200 no GET")
        void retriesThenSucceedsOnGet() {
            AsaasHttpGateway gateway = gateway(10_000);
            enqueueJson(429, "{\"errors\":[{\"description\":\"slow down\"}]}", "0");
            enqueueJson(200, "{\"id\":\"pay_ok\",\"status\":\"PENDING\",\"value\":10.00}");

            AsaasPaymentResponse response = gateway.get(
                    FAKE_API_KEY, "/payments/pay_ok", AsaasPaymentResponse.class);

            assertThat(response.getId()).isEqualTo("pay_ok");
            assertThat(server.getRequestCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("14 API key não aparece em logs")
        void apiKeyIsRedactedFromLogs() {
            AsaasHttpGateway gateway = gateway(10_000);
            enqueueJson(200, "{\"id\":\"pay_log\",\"status\":\"PENDING\",\"value\":1.00}");

            Logger logger = (Logger) LoggerFactory.getLogger(AsaasHttpGateway.class);
            ListAppender<ILoggingEvent> appender = new ListAppender<>();
            appender.start();
            logger.addAppender(appender);
            try {
                gateway.postFinancial(
                        FAKE_API_KEY, "/payments", Map.of("value", 1), "idem-log", AsaasPaymentResponse.class);
            } finally {
                logger.detachAppender(appender);
            }

            String logs = appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .reduce("", (left, right) -> left + "\n" + right);
            assertThat(logs).doesNotContain(FAKE_API_KEY);
            assertThat(logs).doesNotContain("$aact_");
            assertThat(logs).doesNotContain("LiveSecretKeyForHardeningTest");
        }

        private AsaasHttpGateway gateway(int readTimeoutMs) {
            AsaasProperties properties = new AsaasProperties();
            properties.setBaseUrl(server.url("/").toString().replaceAll("/$", ""));
            properties.getTimeout().setConnect(1_000);
            properties.getTimeout().setRead(readTimeoutMs);
            properties.getRetry().setMaxAttempts(3);
            return new AsaasHttpGateway(properties, WebClient.builder(), new ObjectMapper());
        }

        private void enqueueJson(int status, String body) {
            enqueueJson(status, body, null);
        }

        private void enqueueJson(int status, String body, String retryAfterSeconds) {
            MockResponse response = new MockResponse()
                    .setResponseCode(status)
                    .setHeader("Content-Type", "application/json")
                    .setBody(body);
            if (retryAfterSeconds != null) {
                response.setHeader("Retry-After", retryAfterSeconds);
            }
            server.enqueue(response);
        }
    }

    @Nested
    @DisplayName("Webhooks")
    class WebhookTests {

        private Wallet wallet;
        private Transaction transaction;
        private String asaasPaymentId;

        @BeforeEach
        void seedDeposit() {
            Subaccount subaccount = subaccountRepository.save(TestFixtures.aSubaccount(SubaccountStatus.ACTIVE));
            wallet = walletRepository.save(TestFixtures.aWalletWithBalance(subaccount, BigDecimal.ZERO));
            asaasPaymentId = "pay_" + UUID.randomUUID().toString().substring(0, 12);
            transaction = transactionRepository.save(
                    TestFixtures.aPendingDeposit(wallet, new BigDecimal("200.00"), asaasPaymentId));
        }

        @Test
        @DisplayName("8 Duplicate webhook — mesmo id → 200, crédito uma vez")
        void duplicateEventIdCreditsOnce() throws Exception {
            AsaasWebhookPayload payload = paymentPayload("evt_dup", "PAYMENT_CONFIRMED", asaasPaymentId);

            mockMvc.perform(post("/api/v1/webhooks/asaas")
                            .header("asaas-access-token", WEBHOOK_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(payload)))
                    .andExpect(status().isOk());
            mockMvc.perform(post("/api/v1/webhooks/asaas")
                            .header("asaas-access-token", WEBHOOK_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(payload)))
                    .andExpect(status().isOk());

            assertThat(walletRepository.findById(wallet.getId()).orElseThrow().getBalance())
                    .isEqualByComparingTo("200.00");
            assertThat(transactionRepository.findById(transaction.getId()).orElseThrow().getStatus())
                    .isEqualTo(TransactionStatus.COMPLETED);
        }

        @Test
        @DisplayName("9 Invalid webhook — token errado → 401")
        void invalidTokenIsUnauthorized() throws Exception {
            mockMvc.perform(post("/api/v1/webhooks/asaas")
                            .header("asaas-access-token", "wrong-token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    paymentPayload("evt_bad", "PAYMENT_CONFIRMED", asaasPaymentId))))
                    .andExpect(status().isUnauthorized());

            assertThat(walletRepository.findById(wallet.getId()).orElseThrow().getBalance())
                    .isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("10 Process twice — segundo delivery no-op")
        void secondDeliveryIsNoOp() throws Exception {
            mockMvc.perform(post("/api/v1/webhooks/asaas")
                            .header("asaas-access-token", WEBHOOK_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    paymentPayload("evt_first", "PAYMENT_CONFIRMED", asaasPaymentId))))
                    .andExpect(status().isOk());
            mockMvc.perform(post("/api/v1/webhooks/asaas")
                            .header("asaas-access-token", WEBHOOK_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    paymentPayload("evt_second", "PAYMENT_RECEIVED", asaasPaymentId))))
                    .andExpect(status().isOk());

            assertThat(walletRepository.findById(wallet.getId()).orElseThrow().getBalance())
                    .isEqualByComparingTo("200.00");
        }
    }

    @Nested
    @DisplayName("Reconciliation")
    class ReconciliationTests {

        @Test
        @DisplayName("11 Missing confirmation — PROCESSING + PENDING → PENDING")
        void localProcessingAsaasPending() {
            Transaction local = persistDeposit("200.00", TransactionStatus.PROCESSING);
            when(asaasPaymentClient.retrievePayment(any(), eq(local.getAsaasPaymentId())))
                    .thenReturn(AsaasPaymentResponse.builder()
                            .id(local.getAsaasPaymentId())
                            .status("PENDING")
                            .value(new BigDecimal("200.00"))
                            .build());

            AsaasReconciliation result = reconciliationService.reconcile(local.getId());

            assertThat(result.getStatus()).isEqualTo(ReconciliationStatus.PENDING);
            assertThat(result.getLocalStatus()).isEqualTo("PROCESSING");
            assertThat(result.getAsaasStatus()).isEqualTo("PENDING");
        }

        @Test
        @DisplayName("12 Missing local — Asaas id sem row Theron → FAILED")
        void asaasWithoutTheron() {
            AsaasReconciliation result = reconciliationService.reconcileAsaasResource(
                    "pay_orphan", ReconciliationKind.PAYMENT);

            assertThat(result.getStatus()).isEqualTo(ReconciliationStatus.FAILED);
            assertThat(result.getDetail()).contains("Asaas without Theron");
            assertThat(result.getTransaction()).isNull();
        }

        @Test
        @DisplayName("13 Divergence — amounts diferentes → DIVERGENT")
        void divergentAmounts() {
            Transaction local = persistDeposit("200.00", TransactionStatus.COMPLETED);
            when(asaasPaymentClient.retrievePayment(any(), eq(local.getAsaasPaymentId())))
                    .thenReturn(AsaasPaymentResponse.builder()
                            .id(local.getAsaasPaymentId())
                            .status("RECEIVED")
                            .value(new BigDecimal("50.00"))
                            .build());

            AsaasReconciliation result = reconciliationService.reconcile(local.getId());

            assertThat(result.getStatus()).isEqualTo(ReconciliationStatus.DIVERGENT);
            assertThat(result.getLocalAmount()).isEqualByComparingTo("200.00");
            assertThat(result.getAsaasAmount()).isEqualByComparingTo("50.00");
        }

        private Transaction persistDeposit(String amount, TransactionStatus status) {
            Subaccount subaccount = subaccountRepository.save(TestFixtures.aSubaccount(SubaccountStatus.ACTIVE));
            Wallet wallet = walletRepository.save(TestFixtures.aWalletWithBalance(subaccount, BigDecimal.ZERO));
            Transaction transaction = TestFixtures.aPendingDeposit(
                    wallet, new BigDecimal(amount), "pay_" + UUID.randomUUID().toString().substring(0, 12));
            transaction.setStatus(status);
            return transactionRepository.save(transaction);
        }
    }

    @Test
    @DisplayName("15 API key não aparece em SubaccountResponse / ApiErrorResponse")
    void apiKeyDoesNotLeakInResponses() throws Exception {
        Subaccount subaccount = TestFixtures.aSubaccount(SubaccountStatus.ACTIVE);
        subaccount.setEncryptedApiKey(FAKE_API_KEY.getBytes());
        SubaccountResponse response = SubaccountMapper.toResponse(subaccountRepository.save(subaccount));
        String subaccountJson = objectMapper.writeValueAsString(response);

        assertThat(subaccountJson).doesNotContain(FAKE_API_KEY);
        assertThat(subaccountJson).doesNotContain("$aact_");
        assertThat(subaccountJson.toLowerCase()).doesNotContain("apikey");

        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        org.springframework.mock.web.MockHttpServletRequest request =
                new org.springframework.mock.web.MockHttpServletRequest();
        request.setRequestURI("/api/v1/deposits");
        AsaasApiException ex = new AsaasApiException(
                "provider error",
                400,
                "{\"errors\":[{\"description\":\"invalid access_token=" + FAKE_API_KEY + "\"}]}");
        ApiErrorResponse body = handler.handleAsaasApiException(ex, request).getBody();

        assertThat(body).isNotNull();
        assertThat(body.getMessage()).doesNotContain(FAKE_API_KEY);
        assertThat(body.getMessage()).doesNotContain("$aact_");
    }

    @Test
    @DisplayName("16 Nenhum Controller tem Asaas*Client ou WebClient")
    void controllersDoNotInjectAsaasClients() {
        assertThat(WebhookController.class.getDeclaredFields())
                .noneMatch(field -> field.getType().getSimpleName().contains("AsaasProperties"));

        applicationContext.getBeansWithAnnotation(RestController.class).values()
                .forEach(bean -> assertNoAsaasClientOrWebClient(bean));
        applicationContext.getBeansWithAnnotation(Controller.class).values()
                .forEach(bean -> assertNoAsaasClientOrWebClient(bean));
    }

    private static void assertNoAsaasClientOrWebClient(Object bean) {
        Class<?> type = ClassUtils.getUserClass(bean);
        if (!type.getPackageName().equals("com.theron.wallet.controller")) {
            return;
        }
        for (Field field : type.getDeclaredFields()) {
            Class<?> fieldType = field.getType();
            assertThat(WebClient.class.isAssignableFrom(fieldType))
                    .as("%s.%s", type.getSimpleName(), field.getName())
                    .isFalse();
            assertThat(fieldType.getSimpleName().matches("Asaas.*Client"))
                    .as("%s.%s", type.getSimpleName(), field.getName())
                    .isFalse();
        }
    }

    private static AsaasWebhookPayload paymentPayload(String eventId, String event, String paymentId) {
        return AsaasWebhookPayload.builder()
                .id(eventId)
                .event(event)
                .payment(AsaasWebhookPayload.Payment.builder()
                        .id(paymentId)
                        .value(new BigDecimal("200.00"))
                        .build())
                .build();
    }
}
