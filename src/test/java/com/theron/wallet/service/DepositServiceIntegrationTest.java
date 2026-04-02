package com.theron.wallet.service;

import com.theron.wallet.BaseIntegrationTest;
import com.theron.wallet.TestFixtures;
import com.theron.wallet.dto.asaas.AsaasPaymentResponse;
import com.theron.wallet.dto.request.DepositRequest;
import com.theron.wallet.dto.response.DepositResponse;
import com.theron.wallet.entity.Customer;
import com.theron.wallet.entity.Transaction;
import com.theron.wallet.entity.Wallet;
import com.theron.wallet.enums.TransactionStatus;
import com.theron.wallet.enums.TransactionType;
import com.theron.wallet.exception.AsaasApiException;
import com.theron.wallet.exception.ResourceNotFoundException;
import com.theron.wallet.exception.SubaccountOperationBlockedException;
import com.theron.wallet.repository.CustomerRepository;
import com.theron.wallet.repository.TransactionRepository;
import com.theron.wallet.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DepositServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private DepositService depositService;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    private Customer savedCustomer;

    @BeforeEach
    void setUp() {
        savedCustomer = customerRepository.save(TestFixtures.aCustomer());
        when(asaasApiKeyResolver.resolveForOutbound(any())).thenReturn("root-api-key");
    }

    @Test
    @DisplayName("should create PIX deposit, persist PENDING transaction, and link Asaas payment ID")
    void shouldCreatePixDeposit() {
        String asaasPaymentId = "pay_" + UUID.randomUUID().toString().substring(0, 16);
        AsaasPaymentResponse mockResponse = AsaasPaymentResponse.builder()
                .id(asaasPaymentId)
                .customer(savedCustomer.getAsaasCustomerId())
                .billingType("PIX")
                .value(new BigDecimal("50.00"))
                .status("PENDING")
                .build();

        when(asaasPaymentClient.createPayment(anyString(), any())).thenReturn(mockResponse);

        DepositRequest request = DepositRequest.builder()
                .customerId(savedCustomer.getId())
                .amount(new BigDecimal("50.00"))
                .description("Test PIX deposit")
                .build();

        DepositResponse response = depositService.createPixDeposit(request);

        assertThat(response.getTransactionId()).isNotNull();
        assertThat(response.getAmount()).isEqualByComparingTo(new BigDecimal("50.00"));
        assertThat(response.getStatus()).isEqualTo("PENDING");
        assertThat(response.getAsaasPaymentId()).isEqualTo(asaasPaymentId);
        assertThat(response.getDescription()).isEqualTo("Test PIX deposit");

        Transaction persisted = transactionRepository.findById(response.getTransactionId()).orElseThrow();
        assertThat(persisted.getType()).isEqualTo(TransactionType.DEPOSIT);
        assertThat(persisted.getStatus()).isEqualTo(TransactionStatus.PENDING);
        assertThat(persisted.getAsaasPaymentId()).isEqualTo(asaasPaymentId);
        assertThat(persisted.getIdempotencyKey()).isNotBlank();
    }

    @Test
    @DisplayName("should auto-create wallet when customer has no wallet yet")
    void shouldAutoCreateWallet() {
        when(asaasPaymentClient.createPayment(anyString(), any()))
                .thenReturn(AsaasPaymentResponse.builder().id("pay_new").build());

        assertThat(walletRepository.findByCustomerId(savedCustomer.getId())).isEmpty();

        DepositRequest request = DepositRequest.builder()
                .customerId(savedCustomer.getId())
                .amount(new BigDecimal("25.00"))
                .build();

        DepositResponse response = depositService.createPixDeposit(request);

        Optional<Wallet> wallet = walletRepository.findByCustomerId(savedCustomer.getId());
        assertThat(wallet).isPresent();
        assertThat(wallet.get().getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.getWalletId()).isEqualTo(wallet.get().getId());
    }

    @Test
    @DisplayName("should throw ResourceNotFoundException when customer does not exist")
    void shouldThrowWhenCustomerNotFound() {
        DepositRequest request = DepositRequest.builder()
                .customerId(UUID.randomUUID())
                .amount(new BigDecimal("10.00"))
                .build();

        assertThatThrownBy(() -> depositService.createPixDeposit(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Customer not found");
    }

    @Test
    @DisplayName("should throw ResourceNotFoundException when customer is not synced with Asaas")
    void shouldThrowWhenCustomerNotSynced() {
        Customer unsyncedCustomer = customerRepository.save(TestFixtures.aCustomerWithoutAsaas());

        DepositRequest request = DepositRequest.builder()
                .customerId(unsyncedCustomer.getId())
                .amount(new BigDecimal("10.00"))
                .build();

        assertThatThrownBy(() -> depositService.createPixDeposit(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("not synced with Asaas");
    }

    @Test
    @DisplayName("should rollback entire transaction when Asaas API call fails")
    void shouldRollbackWhenAsaasFails() {
        when(asaasPaymentClient.createPayment(anyString(), any()))
                .thenThrow(new AsaasApiException("Asaas error", 500, "Internal Server Error"));

        DepositRequest request = DepositRequest.builder()
                .customerId(savedCustomer.getId())
                .amount(new BigDecimal("75.00"))
                .build();

        assertThatThrownBy(() -> depositService.createPixDeposit(request))
                .isInstanceOf(AsaasApiException.class);

        assertThat(transactionRepository.findAll()).isEmpty();
    }

    // --- Tenant-aware deposit tests ---

    @Test
    @DisplayName("should use subaccount key when resolver returns it for ACTIVE subaccount")
    void shouldUseSubaccountKeyForActiveSubaccount() {
        String subaccountKey = "sub_key_active_123";
        when(asaasApiKeyResolver.resolveForOutbound(savedCustomer.getId())).thenReturn(subaccountKey);

        String asaasPaymentId = "pay_sub_" + UUID.randomUUID().toString().substring(0, 12);
        when(asaasPaymentClient.createPayment(eq(subaccountKey), any()))
                .thenReturn(AsaasPaymentResponse.builder().id(asaasPaymentId).build());

        DepositRequest request = DepositRequest.builder()
                .customerId(savedCustomer.getId())
                .amount(new BigDecimal("100.00"))
                .description("Subaccount deposit")
                .build();

        DepositResponse response = depositService.createPixDeposit(request);

        assertThat(response.getAsaasPaymentId()).isEqualTo(asaasPaymentId);
        verify(asaasPaymentClient).createPayment(eq(subaccountKey), any());
        verify(asaasPaymentClient, never()).createPayment(eq("root-api-key"), any());
    }

    @Test
    @DisplayName("should reject deposit when resolver throws for EVALUATION_BLOCKED subaccount")
    void shouldRejectDepositWhenSubaccountBlocked() {
        when(asaasApiKeyResolver.resolveForOutbound(savedCustomer.getId()))
                .thenThrow(new SubaccountOperationBlockedException(
                        "Subaccount is EVALUATION_BLOCKED — outbound operations are not allowed"));

        DepositRequest request = DepositRequest.builder()
                .customerId(savedCustomer.getId())
                .amount(new BigDecimal("50.00"))
                .build();

        assertThatThrownBy(() -> depositService.createPixDeposit(request))
                .isInstanceOf(SubaccountOperationBlockedException.class)
                .hasMessageContaining("EVALUATION_BLOCKED");

        verify(asaasPaymentClient, never()).createPayment(anyString(), any());
        assertThat(transactionRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("should use root key when customer has no subaccount")
    void shouldUseRootKeyWhenNoSubaccount() {
        String rootKey = "root_api_key_xyz";
        when(asaasApiKeyResolver.resolveForOutbound(savedCustomer.getId())).thenReturn(rootKey);
        when(asaasPaymentClient.createPayment(eq(rootKey), any()))
                .thenReturn(AsaasPaymentResponse.builder().id("pay_root_1").build());

        DepositRequest request = DepositRequest.builder()
                .customerId(savedCustomer.getId())
                .amount(new BigDecimal("30.00"))
                .build();

        DepositResponse response = depositService.createPixDeposit(request);

        assertThat(response.getAsaasPaymentId()).isEqualTo("pay_root_1");
        verify(asaasPaymentClient).createPayment(eq(rootKey), any());
    }
}
