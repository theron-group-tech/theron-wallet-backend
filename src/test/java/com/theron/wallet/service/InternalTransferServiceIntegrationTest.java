package com.theron.wallet.service;

import com.theron.wallet.BaseIntegrationTest;
import com.theron.wallet.TestFixtures;
import com.theron.wallet.dto.request.InternalTransferRequest;
import com.theron.wallet.dto.response.InternalTransferResponse;
import com.theron.wallet.entity.Subaccount;
import com.theron.wallet.entity.Transaction;
import com.theron.wallet.entity.Wallet;
import com.theron.wallet.enums.SubaccountStatus;
import com.theron.wallet.enums.TransactionStatus;
import com.theron.wallet.enums.TransactionType;
import com.theron.wallet.exception.InsufficientBalanceException;
import com.theron.wallet.exception.ResourceNotFoundException;
import com.theron.wallet.exception.SelfTransferException;
import com.theron.wallet.repository.SubaccountRepository;
import com.theron.wallet.repository.TransactionRepository;
import com.theron.wallet.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InternalTransferServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private InternalTransferService internalTransferService;

    @Autowired
    private SubaccountRepository subaccountRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    private Subaccount sender;
    private Subaccount receiver;
    private Wallet senderWallet;
    private Wallet receiverWallet;

    @BeforeEach
    void setUp() {
        sender = subaccountRepository.save(TestFixtures.aSubaccount(SubaccountStatus.ACTIVE));
        receiver = subaccountRepository.save(TestFixtures.aSubaccount("98765432100", SubaccountStatus.ACTIVE));
        senderWallet = walletRepository.save(TestFixtures.aWalletWithBalance(sender, new BigDecimal("500.00")));
        receiverWallet = walletRepository.save(TestFixtures.aWallet(receiver));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Happy path
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Internal transfer — happy path")
    class HappyPathTests {

        @Test
        @DisplayName("should debit sender, credit receiver, and create two COMPLETED transactions")
        void shouldTransferSuccessfully() {
            InternalTransferRequest request = TestFixtures.anInternalTransferRequest(
                    sender.getId(), receiver.getId(), new BigDecimal("150.00"));

            InternalTransferResponse response = internalTransferService.transfer(request);

            // Response assertions
            assertThat(response.getSenderTransactionId()).isNotNull();
            assertThat(response.getReceiverTransactionId()).isNotNull();
            assertThat(response.getSenderTransactionId()).isNotEqualTo(response.getReceiverTransactionId());
            assertThat(response.getSenderWalletId()).isEqualTo(senderWallet.getId());
            assertThat(response.getReceiverWalletId()).isEqualTo(receiverWallet.getId());
            assertThat(response.getAmount()).isEqualByComparingTo(new BigDecimal("150.00"));
            assertThat(response.getStatus()).isEqualTo("COMPLETED");
            assertThat(response.getDescription()).isEqualTo("Test internal transfer");
            assertThat(response.getIdempotencyKey()).isNotBlank();
            assertThat(response.getCreatedAt()).isNotNull();

            // Sender wallet debited
            Wallet updatedSender = walletRepository.findById(senderWallet.getId()).orElseThrow();
            assertThat(updatedSender.getBalance()).isEqualByComparingTo(new BigDecimal("350.00"));

            // Receiver wallet credited
            Wallet updatedReceiver = walletRepository.findById(receiverWallet.getId()).orElseThrow();
            assertThat(updatedReceiver.getBalance()).isEqualByComparingTo(new BigDecimal("150.00"));

            // Two transactions exist
            List<Transaction> allTransactions = transactionRepository.findAll();
            assertThat(allTransactions).hasSize(2);

            // Sender transaction
            Transaction senderTx = transactionRepository
                    .findById(response.getSenderTransactionId()).orElseThrow();
            assertThat(senderTx.getType()).isEqualTo(TransactionType.TRANSFER_OUT);
            assertThat(senderTx.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
            assertThat(senderTx.getAmount()).isEqualByComparingTo(new BigDecimal("150.00"));
            assertThat(senderTx.getIdempotencyKey()).isNotBlank();

            // Receiver transaction
            Transaction receiverTx = transactionRepository
                    .findById(response.getReceiverTransactionId()).orElseThrow();
            assertThat(receiverTx.getType()).isEqualTo(TransactionType.TRANSFER_IN);
            assertThat(receiverTx.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
            assertThat(receiverTx.getAmount()).isEqualByComparingTo(new BigDecimal("150.00"));
            assertThat(receiverTx.getExternalReference()).isEqualTo(senderTx.getId().toString());
        }

        @Test
        @DisplayName("should drain sender wallet to zero when full balance is transferred")
        void shouldAllowFullBalanceTransfer() {
            InternalTransferRequest request = TestFixtures.anInternalTransferRequest(
                    sender.getId(), receiver.getId(), new BigDecimal("500.00"));

            InternalTransferResponse response = internalTransferService.transfer(request);

            assertThat(response.getStatus()).isEqualTo("COMPLETED");

            Wallet updatedSender = walletRepository.findById(senderWallet.getId()).orElseThrow();
            assertThat(updatedSender.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);

            Wallet updatedReceiver = walletRepository.findById(receiverWallet.getId()).orElseThrow();
            assertThat(updatedReceiver.getBalance()).isEqualByComparingTo(new BigDecimal("500.00"));
        }

        @Test
        @DisplayName("should correctly link receiver transaction to sender via externalReference")
        void shouldLinkReceiverTransactionToSender() {
            InternalTransferRequest request = TestFixtures.anInternalTransferRequest(
                    sender.getId(), receiver.getId(), new BigDecimal("75.00"));

            InternalTransferResponse response = internalTransferService.transfer(request);

            Transaction senderTx = transactionRepository
                    .findById(response.getSenderTransactionId()).orElseThrow();
            Transaction receiverTx = transactionRepository
                    .findById(response.getReceiverTransactionId()).orElseThrow();

            assertThat(receiverTx.getExternalReference()).isEqualTo(senderTx.getId().toString());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Balance validation
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Internal transfer — balance validation")
    class BalanceValidationTests {

        @Test
        @DisplayName("should throw InsufficientBalanceException when sender balance is too low")
        void shouldRejectInsufficientBalance() {
            InternalTransferRequest request = TestFixtures.anInternalTransferRequest(
                    sender.getId(), receiver.getId(), new BigDecimal("600.00"));

            assertThatThrownBy(() -> internalTransferService.transfer(request))
                    .isInstanceOf(InsufficientBalanceException.class)
                    .hasMessageContaining("Insufficient balance");

            // Wallets must remain unchanged
            Wallet unchangedSender = walletRepository.findById(senderWallet.getId()).orElseThrow();
            assertThat(unchangedSender.getBalance()).isEqualByComparingTo(new BigDecimal("500.00"));

            Wallet unchangedReceiver = walletRepository.findById(receiverWallet.getId()).orElseThrow();
            assertThat(unchangedReceiver.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);

            // No transactions created
            assertThat(transactionRepository.findAll()).isEmpty();
        }

        @Test
        @DisplayName("should reject transfer when sender wallet has exactly zero balance")
        void shouldRejectZeroBalance() {
            Subaccount zeroSender = subaccountRepository.save(
                    TestFixtures.aSubaccount("11111111111", SubaccountStatus.ACTIVE));
            walletRepository.save(TestFixtures.aWalletWithBalance(zeroSender, BigDecimal.ZERO));

            InternalTransferRequest request = TestFixtures.anInternalTransferRequest(
                    zeroSender.getId(), receiver.getId(), new BigDecimal("0.01"));

            assertThatThrownBy(() -> internalTransferService.transfer(request))
                    .isInstanceOf(InsufficientBalanceException.class);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Self-transfer guard
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Internal transfer — self-transfer guard")
    class SelfTransferTests {

        @Test
        @DisplayName("should throw SelfTransferException when sender and receiver are the same subaccount")
        void shouldRejectSelfTransfer() {
            InternalTransferRequest request = TestFixtures.anInternalTransferRequest(
                    sender.getId(), sender.getId(), new BigDecimal("100.00"));

            assertThatThrownBy(() -> internalTransferService.transfer(request))
                    .isInstanceOf(SelfTransferException.class)
                    .hasMessageContaining("Sender and receiver must be different subaccounts");

            // Wallet balance untouched
            Wallet unchangedSender = walletRepository.findById(senderWallet.getId()).orElseThrow();
            assertThat(unchangedSender.getBalance()).isEqualByComparingTo(new BigDecimal("500.00"));

            // No transactions created
            assertThat(transactionRepository.findAll()).isEmpty();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Resource not found
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Internal transfer — resource not found")
    class ResourceNotFoundTests {

        @Test
        @DisplayName("should throw ResourceNotFoundException when sender subaccount does not exist")
        void shouldRejectUnknownSender() {
            InternalTransferRequest request = TestFixtures.anInternalTransferRequest(
                    UUID.randomUUID(), receiver.getId(), new BigDecimal("100.00"));

            assertThatThrownBy(() -> internalTransferService.transfer(request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Subaccount");
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when receiver subaccount does not exist")
        void shouldRejectUnknownReceiver() {
            InternalTransferRequest request = TestFixtures.anInternalTransferRequest(
                    sender.getId(), UUID.randomUUID(), new BigDecimal("100.00"));

            assertThatThrownBy(() -> internalTransferService.transfer(request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Subaccount");
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when sender has no wallet")
        void shouldRejectSenderWithoutWallet() {
            Subaccount noWalletSender = subaccountRepository.save(
                    TestFixtures.aSubaccount("22233344455", SubaccountStatus.ACTIVE));

            InternalTransferRequest request = TestFixtures.anInternalTransferRequest(
                    noWalletSender.getId(), receiver.getId(), new BigDecimal("100.00"));

            assertThatThrownBy(() -> internalTransferService.transfer(request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Wallet");
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when receiver has no wallet")
        void shouldRejectReceiverWithoutWallet() {
            Subaccount noWalletReceiver = subaccountRepository.save(
                    TestFixtures.aSubaccount("55544433322", SubaccountStatus.ACTIVE));

            InternalTransferRequest request = TestFixtures.anInternalTransferRequest(
                    sender.getId(), noWalletReceiver.getId(), new BigDecimal("100.00"));

            assertThatThrownBy(() -> internalTransferService.transfer(request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Wallet");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Idempotency
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Internal transfer — idempotency")
    class IdempotencyTests {

        @Test
        @DisplayName("should return identical response on duplicate idempotency key without re-processing")
        void shouldReturnExistingTransferOnDuplicateKey() {
            String idempotencyKey = "transfer-idem-key-" + UUID.randomUUID();

            InternalTransferRequest request = TestFixtures.anInternalTransferRequestWithKey(
                    sender.getId(), receiver.getId(), new BigDecimal("100.00"), idempotencyKey);

            InternalTransferResponse first = internalTransferService.transfer(request);
            InternalTransferResponse second = internalTransferService.transfer(request);

            // Both calls return same transaction IDs
            assertThat(second.getSenderTransactionId()).isEqualTo(first.getSenderTransactionId());
            assertThat(second.getReceiverTransactionId()).isEqualTo(first.getReceiverTransactionId());
            assertThat(second.getIdempotencyKey()).isEqualTo(first.getIdempotencyKey());

            // Sender debited only once: 500 - 100 = 400
            Wallet updatedSender = walletRepository.findById(senderWallet.getId()).orElseThrow();
            assertThat(updatedSender.getBalance()).isEqualByComparingTo(new BigDecimal("400.00"));

            // Receiver credited only once: 0 + 100 = 100
            Wallet updatedReceiver = walletRepository.findById(receiverWallet.getId()).orElseThrow();
            assertThat(updatedReceiver.getBalance()).isEqualByComparingTo(new BigDecimal("100.00"));

            // Exactly two transactions total (one pair, not two pairs)
            assertThat(transactionRepository.findAll()).hasSize(2);
        }

        @Test
        @DisplayName("should allow two separate transfers with different idempotency keys")
        void shouldProcessIndependentTransfersWithDifferentKeys() {
            InternalTransferRequest first = TestFixtures.anInternalTransferRequestWithKey(
                    sender.getId(), receiver.getId(), new BigDecimal("100.00"), "key-alpha");
            InternalTransferRequest second = TestFixtures.anInternalTransferRequestWithKey(
                    sender.getId(), receiver.getId(), new BigDecimal("100.00"), "key-beta");

            InternalTransferResponse r1 = internalTransferService.transfer(first);
            InternalTransferResponse r2 = internalTransferService.transfer(second);

            assertThat(r1.getSenderTransactionId()).isNotEqualTo(r2.getSenderTransactionId());

            // Sender debited twice: 500 - 100 - 100 = 300
            Wallet updatedSender = walletRepository.findById(senderWallet.getId()).orElseThrow();
            assertThat(updatedSender.getBalance()).isEqualByComparingTo(new BigDecimal("300.00"));

            // Four transactions in total (two pairs)
            assertThat(transactionRepository.findAll()).hasSize(4);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Concurrency
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Internal transfer — concurrency safety")
    class ConcurrencyTests {

        @Test
        @DisplayName("should apply all concurrent transfers exactly once without lost updates")
        void shouldHandleConcurrentTransfersSafely() throws Exception {
            // Both transfers are well within the 500.00 balance — both must succeed.
            // The pessimistic lock ordering guarantees no deadlocks.
            int threadCount = 2;
            BigDecimal transferAmount = new BigDecimal("200.00");

            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            List<Future<InternalTransferResponse>> futures = new ArrayList<>();

            ExecutorService pool = Executors.newFixedThreadPool(threadCount);

            for (int i = 0; i < threadCount; i++) {
                Future<InternalTransferResponse> future = pool.submit(() -> {
                    try {
                        startLatch.await(); // wait until all threads are ready
                        InternalTransferRequest req = TestFixtures.anInternalTransferRequest(
                                sender.getId(), receiver.getId(), transferAmount);
                        return internalTransferService.transfer(req);
                    } finally {
                        doneLatch.countDown();
                    }
                });
                futures.add(future);
            }

            startLatch.countDown(); // release all threads simultaneously
            doneLatch.await();
            pool.shutdown();

            // All futures completed without exception
            for (Future<InternalTransferResponse> f : futures) {
                InternalTransferResponse result = f.get(); // throws if the task threw
                assertThat(result.getStatus()).isEqualTo("COMPLETED");
            }

            // Final sender balance: 500 - (200 * 2) = 100
            Wallet finalSender = walletRepository.findById(senderWallet.getId()).orElseThrow();
            assertThat(finalSender.getBalance()).isEqualByComparingTo(new BigDecimal("100.00"));

            // Final receiver balance: 0 + (200 * 2) = 400
            Wallet finalReceiver = walletRepository.findById(receiverWallet.getId()).orElseThrow();
            assertThat(finalReceiver.getBalance()).isEqualByComparingTo(new BigDecimal("400.00"));

            // Exactly 4 transactions: 2 pairs of TRANSFER_OUT + TRANSFER_IN
            assertThat(transactionRepository.findAll()).hasSize(4);
        }

        @Test
        @DisplayName("should stop at balance exhaustion under concurrent load without negative balance")
        void shouldNeverProduceNegativeBalance() throws Exception {
            // Attempt 3 transfers of 200.00 each from a 500.00 wallet.
            // Exactly 2 should succeed; the third must fail with InsufficientBalanceException.
            int threadCount = 3;
            BigDecimal transferAmount = new BigDecimal("200.00");

            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            List<Future<InternalTransferResponse>> futures = new ArrayList<>();

            ExecutorService pool = Executors.newFixedThreadPool(threadCount);

            for (int i = 0; i < threadCount; i++) {
                Future<InternalTransferResponse> future = pool.submit(() -> {
                    try {
                        startLatch.await();
                        InternalTransferRequest req = TestFixtures.anInternalTransferRequest(
                                sender.getId(), receiver.getId(), transferAmount);
                        return internalTransferService.transfer(req);
                    } finally {
                        doneLatch.countDown();
                    }
                });
                futures.add(future);
            }

            startLatch.countDown();
            doneLatch.await();
            pool.shutdown();

            long successCount = futures.stream().filter(f -> {
                try {
                    f.get();
                    return true;
                } catch (Exception e) {
                    return false;
                }
            }).count();

            // Exactly 2 of the 3 transfers should succeed (500 / 200 = 2 full transfers)
            assertThat(successCount).isEqualTo(2);

            // Balance must be exactly 100.00, never negative
            Wallet finalSender = walletRepository.findById(senderWallet.getId()).orElseThrow();
            assertThat(finalSender.getBalance()).isEqualByComparingTo(new BigDecimal("100.00"));
            assertThat(finalSender.getBalance()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        }
    }
}
