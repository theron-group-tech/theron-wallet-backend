package com.theron.wallet.service;

import com.theron.wallet.BaseIntegrationTest;
import com.theron.wallet.TestFixtures;
import com.theron.wallet.dto.asaas.AsaasWebhookPayload;
import com.theron.wallet.entity.Subaccount;
import com.theron.wallet.entity.Transaction;
import com.theron.wallet.entity.Wallet;
import com.theron.wallet.enums.SubaccountStatus;
import com.theron.wallet.enums.TransactionStatus;
import com.theron.wallet.enums.TransactionType;
import com.theron.wallet.repository.SubaccountRepository;
import com.theron.wallet.repository.TransactionRepository;
import com.theron.wallet.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class WebhookServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private WebhookService webhookService;

    @Autowired
    private SubaccountRepository subaccountRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    private Wallet savedWallet;
    private Transaction savedTransaction;
    private String asaasPaymentId;

    private String asaasAccountId;

    @BeforeEach
    void setUp() {
        Subaccount subaccount = TestFixtures.aSubaccount(SubaccountStatus.ACTIVE);
        asaasAccountId = "acc_" + UUID.randomUUID().toString().substring(0, 16);
        subaccount.setAsaasAccountId(asaasAccountId);
        subaccount = subaccountRepository.save(subaccount);
        savedWallet = walletRepository.save(TestFixtures.aWalletWithBalance(subaccount, BigDecimal.ZERO));

        asaasPaymentId = "pay_" + UUID.randomUUID().toString().substring(0, 16);
        savedTransaction = transactionRepository.save(
                TestFixtures.aPendingDeposit(savedWallet, new BigDecimal("200.00"), asaasPaymentId));
    }

    @Nested
    @DisplayName("Webhook confirmation flow")
    class ConfirmationTests {

        @Test
        @DisplayName("PAYMENT_CONFIRMED should mark transaction CONFIRMED and credit wallet balance")
        void shouldConfirmPaymentAndCreditWallet() {
            AsaasWebhookPayload payload = buildPayload("PAYMENT_CONFIRMED", asaasPaymentId);

            webhookService.processPaymentWebhook(payload);

            Transaction updated = transactionRepository.findById(savedTransaction.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(TransactionStatus.COMPLETED);

            Wallet updatedWallet = walletRepository.findById(savedWallet.getId()).orElseThrow();
            assertThat(updatedWallet.getBalance()).isEqualByComparingTo(new BigDecimal("200.00"));
        }

        @Test
        @DisplayName("PAYMENT_RECEIVED should also confirm and credit the wallet")
        void shouldHandlePaymentReceived() {
            AsaasWebhookPayload payload = buildPayload("PAYMENT_RECEIVED", asaasPaymentId);

            webhookService.processPaymentWebhook(payload);

            Transaction updated = transactionRepository.findById(savedTransaction.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(TransactionStatus.COMPLETED);

            Wallet updatedWallet = walletRepository.findById(savedWallet.getId()).orElseThrow();
            assertThat(updatedWallet.getBalance()).isEqualByComparingTo(new BigDecimal("200.00"));
        }
    }

    @Nested
    @DisplayName("Duplicate webhook delivery")
    class DuplicateTests {

        @Test
        @DisplayName("second PAYMENT_CONFIRMED for same payment should NOT double-credit the wallet")
        void shouldNotDoubleCreditOnDuplicateWebhook() {
            AsaasWebhookPayload payload = buildPayload("PAYMENT_CONFIRMED", asaasPaymentId);

            webhookService.processPaymentWebhook(payload);
            webhookService.processPaymentWebhook(payload);

            Wallet updatedWallet = walletRepository.findById(savedWallet.getId()).orElseThrow();
            assertThat(updatedWallet.getBalance()).isEqualByComparingTo(new BigDecimal("200.00"));
        }

        @Test
        @DisplayName("PAYMENT_RECEIVED after PAYMENT_CONFIRMED should be a no-op")
        void shouldIgnoreReceivedAfterConfirmed() {
            webhookService.processPaymentWebhook(buildPayload("PAYMENT_CONFIRMED", asaasPaymentId));
            webhookService.processPaymentWebhook(buildPayload("PAYMENT_RECEIVED", asaasPaymentId));

            Wallet updatedWallet = walletRepository.findById(savedWallet.getId()).orElseThrow();
            assertThat(updatedWallet.getBalance()).isEqualByComparingTo(new BigDecimal("200.00"));

            Transaction updated = transactionRepository.findById(savedTransaction.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        }

        @Test
        @DisplayName("PAYMENT_CONFIRMED after PAYMENT_DELETED should be a no-op (cancelled is terminal)")
        void shouldIgnoreConfirmedAfterCancelled() {
            webhookService.processPaymentWebhook(buildPayload("PAYMENT_DELETED", asaasPaymentId));
            webhookService.processPaymentWebhook(buildPayload("PAYMENT_CONFIRMED", asaasPaymentId));

            Wallet updatedWallet = walletRepository.findById(savedWallet.getId()).orElseThrow();
            assertThat(updatedWallet.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);

            Transaction updated = transactionRepository.findById(savedTransaction.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(TransactionStatus.CANCELLED);
        }
    }

    @Nested
    @DisplayName("Cancellation and failure events")
    class CancellationTests {

        @Test
        @DisplayName("PAYMENT_DELETED should mark transaction CANCELLED and not credit wallet")
        void shouldCancelOnDelete() {
            webhookService.processPaymentWebhook(buildPayload("PAYMENT_DELETED", asaasPaymentId));

            Transaction updated = transactionRepository.findById(savedTransaction.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(TransactionStatus.CANCELLED);

            Wallet updatedWallet = walletRepository.findById(savedWallet.getId()).orElseThrow();
            assertThat(updatedWallet.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("PAYMENT_OVERDUE should mark transaction FAILED")
        void shouldFailOnOverdue() {
            webhookService.processPaymentWebhook(buildPayload("PAYMENT_OVERDUE", asaasPaymentId));

            Transaction updated = transactionRepository.findById(savedTransaction.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(TransactionStatus.FAILED);
        }

        @Test
        @DisplayName("PAYMENT_REFUNDED should mark transaction FAILED")
        void shouldFailOnRefund() {
            when(asaasPaymentClient.retrievePayment(any(), any())).thenReturn(
                    com.theron.wallet.dto.asaas.AsaasPaymentResponse.builder()
                            .id(asaasPaymentId)
                            .status("REFUNDED")
                            .value(new BigDecimal("200.00"))
                            .build());
            webhookService.processPaymentWebhook(buildPayload("PAYMENT_REFUNDED", asaasPaymentId));

            Transaction updated = transactionRepository.findById(savedTransaction.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(TransactionStatus.FAILED);
        }
    }

    @Nested
    @DisplayName("Edge cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("should ignore webhook for unknown payment ID")
        void shouldIgnoreUnknownPaymentId() {
            AsaasWebhookPayload payload = buildPayload("PAYMENT_CONFIRMED", "pay_unknown_id");

            webhookService.processPaymentWebhook(payload);

            Wallet updatedWallet = walletRepository.findById(savedWallet.getId()).orElseThrow();
            assertThat(updatedWallet.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("should ignore webhook with unknown event type")
        void shouldIgnoreUnknownEvent() {
            AsaasWebhookPayload payload = buildPayload("SOME_FUTURE_EVENT", asaasPaymentId);

            webhookService.processPaymentWebhook(payload);

            Transaction updated = transactionRepository.findById(savedTransaction.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(TransactionStatus.PROCESSING);
        }

        @Test
        @DisplayName("should ignore webhook with null payment data")
        void shouldIgnoreNullPayment() {
            AsaasWebhookPayload payload = AsaasWebhookPayload.builder()
                    .event("PAYMENT_CONFIRMED")
                    .payment(null)
                    .build();

            webhookService.processPaymentWebhook(payload);

            Transaction updated = transactionRepository.findById(savedTransaction.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(TransactionStatus.PROCESSING);
        }

        @Test
        @DisplayName("PAYMENT_CREATED should not change transaction status (informational event)")
        void shouldNotActOnCreatedEvent() {
            webhookService.processPaymentWebhook(buildPayload("PAYMENT_CREATED", asaasPaymentId));

            Transaction updated = transactionRepository.findById(savedTransaction.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(TransactionStatus.PROCESSING);
        }
    }

    @Nested
    @DisplayName("Inbound PIX on subaccount without local charge")
    class InboundPixWithoutLocalChargeTests {

        @Test
        @DisplayName("PAYMENT_RECEIVED without local tx credits wallet via TRANSFER_IN")
        void shouldCreditWalletOnInboundPixReceived() {
            String inboundPaymentId = "pay_in_" + UUID.randomUUID().toString().substring(0, 12);
            AsaasWebhookPayload payload = buildInboundPixPayload("PAYMENT_RECEIVED", inboundPaymentId, "100.00");

            webhookService.processPaymentWebhook(payload);

            Wallet updatedWallet = walletRepository.findById(savedWallet.getId()).orElseThrow();
            assertThat(updatedWallet.getBalance()).isEqualByComparingTo(new BigDecimal("100.00"));

            Transaction inbound = transactionRepository.findByAsaasPaymentId(inboundPaymentId).orElseThrow();
            assertThat(inbound.getType()).isEqualTo(TransactionType.TRANSFER_IN);
            assertThat(inbound.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
            assertThat(inbound.getAmount()).isEqualByComparingTo(new BigDecimal("100.00"));
            assertThat(inbound.getIdempotencyKey()).isEqualTo("asaas:pix:in:" + inboundPaymentId);
        }

        @Test
        @DisplayName("second PAYMENT_RECEIVED for the same inbound payment must not double-credit")
        void shouldNotDoubleCreditInboundPix() {
            String inboundPaymentId = "pay_in_" + UUID.randomUUID().toString().substring(0, 12);
            AsaasWebhookPayload payload = buildInboundPixPayload("PAYMENT_RECEIVED", inboundPaymentId, "100.00");

            webhookService.processPaymentWebhook(payload);
            webhookService.processPaymentWebhook(payload);

            Wallet updatedWallet = walletRepository.findById(savedWallet.getId()).orElseThrow();
            assertThat(updatedWallet.getBalance()).isEqualByComparingTo(new BigDecimal("100.00"));
            assertThat(transactionRepository.findByAsaasPaymentId(inboundPaymentId)).isPresent();
        }
    }

    private AsaasWebhookPayload buildInboundPixPayload(String event, String paymentId, String value) {
        return AsaasWebhookPayload.builder()
                .event(event)
                .account(AsaasWebhookPayload.Account.builder().id(asaasAccountId).build())
                .payment(AsaasWebhookPayload.Payment.builder()
                        .id(paymentId)
                        .value(new BigDecimal(value))
                        .status("RECEIVED")
                        .billingType("PIX")
                        .pixQrCodeId("qr_" + paymentId)
                        .pixTransaction("pix_" + paymentId)
                        .build())
                .build();
    }

    private AsaasWebhookPayload buildPayload(String event, String paymentId) {
        return AsaasWebhookPayload.builder()
                .event(event)
                .payment(AsaasWebhookPayload.Payment.builder()
                        .id(paymentId)
                        .value(new BigDecimal("200.00"))
                        .status("RECEIVED")
                        .build())
                .build();
    }
}
