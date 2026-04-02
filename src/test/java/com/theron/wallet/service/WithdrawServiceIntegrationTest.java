package com.theron.wallet.service;

import com.theron.wallet.BaseIntegrationTest;
import com.theron.wallet.TestFixtures;
import com.theron.wallet.dto.asaas.AsaasTransferResponse;
import com.theron.wallet.dto.asaas.AsaasWebhookPayload;
import com.theron.wallet.dto.request.WithdrawRequest;
import com.theron.wallet.dto.response.WithdrawResponse;
import com.theron.wallet.entity.Customer;
import com.theron.wallet.entity.Transaction;
import com.theron.wallet.entity.Wallet;
import com.theron.wallet.enums.TransactionStatus;
import com.theron.wallet.enums.TransactionType;
import com.theron.wallet.exception.AsaasApiException;
import com.theron.wallet.exception.InsufficientBalanceException;
import com.theron.wallet.exception.ResourceNotFoundException;
import com.theron.wallet.exception.SubaccountOperationBlockedException;
import com.theron.wallet.repository.CustomerRepository;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WithdrawServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private WithdrawService withdrawService;

    @Autowired
    private WebhookService webhookService;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    private Customer savedCustomer;
    private Wallet savedWallet;

    @BeforeEach
    void setUp() {
        savedCustomer = customerRepository.save(TestFixtures.aCustomer());
        savedWallet = walletRepository.save(TestFixtures.aWalletWithBalance(savedCustomer, new BigDecimal("500.00")));
        when(asaasApiKeyResolver.resolveForOutbound(any())).thenReturn("root-api-key");
    }

    @Nested
    @DisplayName("Withdraw creation — happy path")
    class HappyPathTests {

        @Test
        @DisplayName("should create withdrawal, debit wallet, and link Asaas transfer ID")
        void shouldCreateWithdrawal() {
            AsaasTransferResponse transferResponse = AsaasTransferResponse.builder()
                    .id("transfer_abc123")
                    .value(new BigDecimal("100.00"))
                    .netValue(new BigDecimal("99.00"))
                    .status("PENDING")
                    .operationType("PIX")
                    .build();

            when(asaasTransferClient.createTransfer(anyString(), any())).thenReturn(transferResponse);

            WithdrawRequest request = TestFixtures.aWithdrawRequest(savedCustomer.getId(), new BigDecimal("100.00"));
            WithdrawResponse response = withdrawService.createWithdraw(request);

            assertThat(response.getTransactionId()).isNotNull();
            assertThat(response.getWalletId()).isEqualTo(savedWallet.getId());
            assertThat(response.getAmount()).isEqualByComparingTo(new BigDecimal("100.00"));
            assertThat(response.getStatus()).isEqualTo("PENDING");
            assertThat(response.getAsaasTransferId()).isEqualTo("transfer_abc123");
            assertThat(response.getDescription()).isEqualTo("Test withdrawal");

            // Verify wallet was debited
            Wallet updatedWallet = walletRepository.findById(savedWallet.getId()).orElseThrow();
            assertThat(updatedWallet.getBalance()).isEqualByComparingTo(new BigDecimal("400.00"));

            // Verify transaction persisted correctly
            Transaction persisted = transactionRepository.findById(response.getTransactionId()).orElseThrow();
            assertThat(persisted.getType()).isEqualTo(TransactionType.WITHDRAWAL);
            assertThat(persisted.getStatus()).isEqualTo(TransactionStatus.PENDING);
            assertThat(persisted.getAsaasPaymentId()).isEqualTo("transfer_abc123");
            assertThat(persisted.getIdempotencyKey()).isNotBlank();
        }

        @Test
        @DisplayName("should withdraw exact wallet balance (drain wallet to zero)")
        void shouldAllowFullBalanceWithdrawal() {
            when(asaasTransferClient.createTransfer(anyString(), any()))
                    .thenReturn(AsaasTransferResponse.builder().id("transfer_full").status("PENDING").build());

            WithdrawRequest request = TestFixtures.aWithdrawRequest(savedCustomer.getId(), new BigDecimal("500.00"));
            WithdrawResponse response = withdrawService.createWithdraw(request);

            assertThat(response.getStatus()).isEqualTo("PENDING");

            Wallet updatedWallet = walletRepository.findById(savedWallet.getId()).orElseThrow();
            assertThat(updatedWallet.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("should use subaccount API key when resolver returns it")
        void shouldUseSubaccountKey() {
            String subaccountKey = "sub_key_xyz";
            when(asaasApiKeyResolver.resolveForOutbound(savedCustomer.getId())).thenReturn(subaccountKey);
            when(asaasTransferClient.createTransfer(eq(subaccountKey), any()))
                    .thenReturn(AsaasTransferResponse.builder().id("transfer_sub").status("PENDING").build());

            WithdrawRequest request = TestFixtures.aWithdrawRequest(savedCustomer.getId(), new BigDecimal("50.00"));
            withdrawService.createWithdraw(request);

            verify(asaasTransferClient).createTransfer(eq(subaccountKey), any());
            verify(asaasTransferClient, never()).createTransfer(eq("root-api-key"), any());
        }
    }

    @Nested
    @DisplayName("Withdraw creation — validation failures")
    class ValidationFailureTests {

        @Test
        @DisplayName("should throw InsufficientBalanceException when balance is less than amount")
        void shouldRejectInsufficientBalance() {
            WithdrawRequest request = TestFixtures.aWithdrawRequest(savedCustomer.getId(), new BigDecimal("600.00"));

            assertThatThrownBy(() -> withdrawService.createWithdraw(request))
                    .isInstanceOf(InsufficientBalanceException.class)
                    .hasMessageContaining("Insufficient balance");

            // Verify wallet was NOT debited
            Wallet wallet = walletRepository.findById(savedWallet.getId()).orElseThrow();
            assertThat(wallet.getBalance()).isEqualByComparingTo(new BigDecimal("500.00"));

            // Verify no transaction was created
            assertThat(transactionRepository.findAll()).isEmpty();

            verify(asaasTransferClient, never()).createTransfer(anyString(), any());
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when customer does not exist")
        void shouldRejectUnknownCustomer() {
            WithdrawRequest request = TestFixtures.aWithdrawRequest(UUID.randomUUID(), new BigDecimal("50.00"));

            assertThatThrownBy(() -> withdrawService.createWithdraw(request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Customer");
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when customer is not synced with Asaas")
        void shouldRejectUnsyncedCustomer() {
            Customer unsynced = customerRepository.save(TestFixtures.aCustomerWithoutAsaas());

            WithdrawRequest request = TestFixtures.aWithdrawRequest(unsynced.getId(), new BigDecimal("50.00"));

            assertThatThrownBy(() -> withdrawService.createWithdraw(request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("not synced with Asaas");
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when customer has no wallet")
        void shouldRejectCustomerWithoutWallet() {
            Customer noWalletCustomer = customerRepository.save(Customer.builder()
                    .name("No Wallet")
                    .email("nowallet@therongroup.com")
                    .cpfCnpj("11122233344")
                    .asaasCustomerId("cus_nowallet")
                    .build());

            WithdrawRequest request = TestFixtures.aWithdrawRequest(noWalletCustomer.getId(), new BigDecimal("50.00"));

            assertThatThrownBy(() -> withdrawService.createWithdraw(request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Wallet");
        }

        @Test
        @DisplayName("should reject withdrawal when subaccount is EVALUATION_BLOCKED")
        void shouldRejectWhenSubaccountBlocked() {
            when(asaasApiKeyResolver.resolveForOutbound(savedCustomer.getId()))
                    .thenThrow(new SubaccountOperationBlockedException(
                            "Subaccount is EVALUATION_BLOCKED — outbound operations are not allowed"));

            WithdrawRequest request = TestFixtures.aWithdrawRequest(savedCustomer.getId(), new BigDecimal("50.00"));

            assertThatThrownBy(() -> withdrawService.createWithdraw(request))
                    .isInstanceOf(SubaccountOperationBlockedException.class)
                    .hasMessageContaining("EVALUATION_BLOCKED");

            // Verify wallet was NOT debited
            Wallet wallet = walletRepository.findById(savedWallet.getId()).orElseThrow();
            assertThat(wallet.getBalance()).isEqualByComparingTo(new BigDecimal("500.00"));

            verify(asaasTransferClient, never()).createTransfer(anyString(), any());
        }
    }

    @Nested
    @DisplayName("Withdraw creation — Asaas API failure and rollback")
    class RollbackTests {

        @Test
        @DisplayName("should rollback wallet debit and transaction when Asaas API call fails")
        void shouldRollbackOnAsaasFailure() {
            when(asaasTransferClient.createTransfer(anyString(), any()))
                    .thenThrow(new AsaasApiException("Asaas error", 500, "Internal Server Error"));

            WithdrawRequest request = TestFixtures.aWithdrawRequest(savedCustomer.getId(), new BigDecimal("100.00"));

            assertThatThrownBy(() -> withdrawService.createWithdraw(request))
                    .isInstanceOf(AsaasApiException.class);

            // Wallet balance must be restored (transaction rolled back)
            Wallet wallet = walletRepository.findById(savedWallet.getId()).orElseThrow();
            assertThat(wallet.getBalance()).isEqualByComparingTo(new BigDecimal("500.00"));

            // No transaction should exist
            assertThat(transactionRepository.findAll()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Idempotency")
    class IdempotencyTests {

        @Test
        @DisplayName("should return existing transaction when same idempotency key is used")
        void shouldReturnExistingOnDuplicateIdempotencyKey() {
            when(asaasTransferClient.createTransfer(anyString(), any()))
                    .thenReturn(AsaasTransferResponse.builder().id("transfer_idem").status("PENDING").build());

            String idempotencyKey = "unique-withdraw-key-123";

            WithdrawRequest request = WithdrawRequest.builder()
                    .customerId(savedCustomer.getId())
                    .amount(new BigDecimal("100.00"))
                    .pixAddressKey("12345678901")
                    .pixAddressKeyType("CPF")
                    .idempotencyKey(idempotencyKey)
                    .build();

            WithdrawResponse first = withdrawService.createWithdraw(request);
            WithdrawResponse second = withdrawService.createWithdraw(request);

            assertThat(second.getTransactionId()).isEqualTo(first.getTransactionId());

            // Wallet should only be debited once
            Wallet wallet = walletRepository.findById(savedWallet.getId()).orElseThrow();
            assertThat(wallet.getBalance()).isEqualByComparingTo(new BigDecimal("400.00"));

            // Only one Asaas API call
            verify(asaasTransferClient).createTransfer(anyString(), any());
        }

        @Test
        @DisplayName("should allow multiple withdrawals with different idempotency keys")
        void shouldAllowDifferentIdempotencyKeys() {
            when(asaasTransferClient.createTransfer(anyString(), any()))
                    .thenReturn(AsaasTransferResponse.builder().id("transfer_1").status("PENDING").build())
                    .thenReturn(AsaasTransferResponse.builder().id("transfer_2").status("PENDING").build());

            WithdrawRequest request1 = WithdrawRequest.builder()
                    .customerId(savedCustomer.getId())
                    .amount(new BigDecimal("100.00"))
                    .pixAddressKey("12345678901")
                    .pixAddressKeyType("CPF")
                    .idempotencyKey("key-1")
                    .build();

            WithdrawRequest request2 = WithdrawRequest.builder()
                    .customerId(savedCustomer.getId())
                    .amount(new BigDecimal("100.00"))
                    .pixAddressKey("12345678901")
                    .pixAddressKeyType("CPF")
                    .idempotencyKey("key-2")
                    .build();

            WithdrawResponse first = withdrawService.createWithdraw(request1);
            WithdrawResponse second = withdrawService.createWithdraw(request2);

            assertThat(second.getTransactionId()).isNotEqualTo(first.getTransactionId());

            Wallet wallet = walletRepository.findById(savedWallet.getId()).orElseThrow();
            assertThat(wallet.getBalance()).isEqualByComparingTo(new BigDecimal("300.00"));
        }
    }

    @Nested
    @DisplayName("Transfer webhook — confirmation and failure")
    class TransferWebhookTests {

        @Test
        @DisplayName("TRANSFER_DONE should mark withdrawal CONFIRMED without changing wallet balance")
        void shouldConfirmTransferDone() {
            // Create a pending withdrawal
            Transaction withdrawal = transactionRepository.save(
                    TestFixtures.aPendingWithdrawal(savedWallet, new BigDecimal("100.00"), "transfer_done_1"));

            // Simulate debit already applied (as it would be during creation)
            savedWallet.debit(new BigDecimal("100.00"));
            walletRepository.save(savedWallet);

            AsaasWebhookPayload payload = buildTransferPayload("TRANSFER_DONE", "transfer_done_1",
                    new BigDecimal("100.00"));

            webhookService.processTransferWebhook(payload);

            Transaction updated = transactionRepository.findById(withdrawal.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(TransactionStatus.CONFIRMED);

            // Wallet balance should remain unchanged (debit already happened)
            Wallet updatedWallet = walletRepository.findById(savedWallet.getId()).orElseThrow();
            assertThat(updatedWallet.getBalance()).isEqualByComparingTo(new BigDecimal("400.00"));
        }

        @Test
        @DisplayName("TRANSFER_FAILED should mark withdrawal FAILED and credit wallet back")
        void shouldCreditBackOnTransferFailed() {
            Transaction withdrawal = transactionRepository.save(
                    TestFixtures.aPendingWithdrawal(savedWallet, new BigDecimal("150.00"), "transfer_fail_1"));

            savedWallet.debit(new BigDecimal("150.00"));
            walletRepository.save(savedWallet);

            AsaasWebhookPayload payload = buildTransferPayload("TRANSFER_FAILED", "transfer_fail_1",
                    new BigDecimal("150.00"));

            webhookService.processTransferWebhook(payload);

            Transaction updated = transactionRepository.findById(withdrawal.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(TransactionStatus.FAILED);

            // Wallet should be credited back
            Wallet updatedWallet = walletRepository.findById(savedWallet.getId()).orElseThrow();
            assertThat(updatedWallet.getBalance()).isEqualByComparingTo(new BigDecimal("500.00"));
        }

        @Test
        @DisplayName("TRANSFER_CANCELLED should mark withdrawal CANCELLED and credit wallet back")
        void shouldCreditBackOnTransferCancelled() {
            Transaction withdrawal = transactionRepository.save(
                    TestFixtures.aPendingWithdrawal(savedWallet, new BigDecimal("200.00"), "transfer_cancel_1"));

            savedWallet.debit(new BigDecimal("200.00"));
            walletRepository.save(savedWallet);

            AsaasWebhookPayload payload = buildTransferPayload("TRANSFER_CANCELLED", "transfer_cancel_1",
                    new BigDecimal("200.00"));

            webhookService.processTransferWebhook(payload);

            Transaction updated = transactionRepository.findById(withdrawal.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(TransactionStatus.CANCELLED);

            Wallet updatedWallet = walletRepository.findById(savedWallet.getId()).orElseThrow();
            assertThat(updatedWallet.getBalance()).isEqualByComparingTo(new BigDecimal("500.00"));
        }

        @Test
        @DisplayName("TRANSFER_BLOCKED should mark withdrawal FAILED and credit wallet back")
        void shouldCreditBackOnTransferBlocked() {
            Transaction withdrawal = transactionRepository.save(
                    TestFixtures.aPendingWithdrawal(savedWallet, new BigDecimal("75.00"), "transfer_blocked_1"));

            savedWallet.debit(new BigDecimal("75.00"));
            walletRepository.save(savedWallet);

            AsaasWebhookPayload payload = buildTransferPayload("TRANSFER_BLOCKED", "transfer_blocked_1",
                    new BigDecimal("75.00"));

            webhookService.processTransferWebhook(payload);

            Transaction updated = transactionRepository.findById(withdrawal.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(TransactionStatus.FAILED);

            Wallet updatedWallet = walletRepository.findById(savedWallet.getId()).orElseThrow();
            assertThat(updatedWallet.getBalance()).isEqualByComparingTo(new BigDecimal("500.00"));
        }

        @Test
        @DisplayName("duplicate TRANSFER_DONE should not double-process already confirmed withdrawal")
        void shouldNotDoubleProcessConfirmedTransfer() {
            Transaction withdrawal = transactionRepository.save(
                    TestFixtures.aPendingWithdrawal(savedWallet, new BigDecimal("100.00"), "transfer_dup_1"));

            savedWallet.debit(new BigDecimal("100.00"));
            walletRepository.save(savedWallet);

            AsaasWebhookPayload payload = buildTransferPayload("TRANSFER_DONE", "transfer_dup_1",
                    new BigDecimal("100.00"));

            webhookService.processTransferWebhook(payload);
            webhookService.processTransferWebhook(payload);

            Transaction updated = transactionRepository.findById(withdrawal.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(TransactionStatus.CONFIRMED);

            Wallet updatedWallet = walletRepository.findById(savedWallet.getId()).orElseThrow();
            assertThat(updatedWallet.getBalance()).isEqualByComparingTo(new BigDecimal("400.00"));
        }

        @Test
        @DisplayName("TRANSFER_FAILED after TRANSFER_DONE should be a no-op (confirmed is terminal)")
        void shouldIgnoreFailureAfterConfirmation() {
            Transaction withdrawal = transactionRepository.save(
                    TestFixtures.aPendingWithdrawal(savedWallet, new BigDecimal("100.00"), "transfer_terminal_1"));

            savedWallet.debit(new BigDecimal("100.00"));
            walletRepository.save(savedWallet);

            webhookService.processTransferWebhook(
                    buildTransferPayload("TRANSFER_DONE", "transfer_terminal_1", new BigDecimal("100.00")));
            webhookService.processTransferWebhook(
                    buildTransferPayload("TRANSFER_FAILED", "transfer_terminal_1", new BigDecimal("100.00")));

            Transaction updated = transactionRepository.findById(withdrawal.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(TransactionStatus.CONFIRMED);

            // Wallet should NOT be credited back (DONE was processed, FAILED ignored)
            Wallet updatedWallet = walletRepository.findById(savedWallet.getId()).orElseThrow();
            assertThat(updatedWallet.getBalance()).isEqualByComparingTo(new BigDecimal("400.00"));
        }

        @Test
        @DisplayName("should ignore transfer webhook for unknown transfer ID")
        void shouldIgnoreUnknownTransferId() {
            AsaasWebhookPayload payload = buildTransferPayload("TRANSFER_DONE", "transfer_unknown",
                    new BigDecimal("100.00"));

            webhookService.processTransferWebhook(payload);

            Wallet updatedWallet = walletRepository.findById(savedWallet.getId()).orElseThrow();
            assertThat(updatedWallet.getBalance()).isEqualByComparingTo(new BigDecimal("500.00"));
        }

        @Test
        @DisplayName("TRANSFER_PENDING should not change transaction status (informational event)")
        void shouldNotActOnPendingEvent() {
            Transaction withdrawal = transactionRepository.save(
                    TestFixtures.aPendingWithdrawal(savedWallet, new BigDecimal("100.00"), "transfer_pending_1"));

            AsaasWebhookPayload payload = buildTransferPayload("TRANSFER_PENDING", "transfer_pending_1",
                    new BigDecimal("100.00"));

            webhookService.processTransferWebhook(payload);

            Transaction updated = transactionRepository.findById(withdrawal.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(TransactionStatus.PENDING);
        }
    }

    @Nested
    @DisplayName("findById")
    class FindByIdTests {

        @Test
        @DisplayName("should return withdrawal by transaction ID")
        void shouldFindWithdrawalById() {
            when(asaasTransferClient.createTransfer(anyString(), any()))
                    .thenReturn(AsaasTransferResponse.builder().id("transfer_find").status("PENDING").build());

            WithdrawRequest request = TestFixtures.aWithdrawRequest(savedCustomer.getId(), new BigDecimal("50.00"));
            WithdrawResponse created = withdrawService.createWithdraw(request);

            WithdrawResponse found = withdrawService.findById(created.getTransactionId());

            assertThat(found.getTransactionId()).isEqualTo(created.getTransactionId());
            assertThat(found.getAsaasTransferId()).isEqualTo("transfer_find");
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException for non-existent transaction")
        void shouldThrowForUnknownId() {
            assertThatThrownBy(() -> withdrawService.findById(UUID.randomUUID()))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Transaction");
        }
    }

    private AsaasWebhookPayload buildTransferPayload(String event, String transferId, BigDecimal value) {
        return AsaasWebhookPayload.builder()
                .event(event)
                .transfer(AsaasWebhookPayload.Transfer.builder()
                        .id(transferId)
                        .value(value)
                        .status(event.replace("TRANSFER_", ""))
                        .operationType("PIX")
                        .build())
                .build();
    }
}
