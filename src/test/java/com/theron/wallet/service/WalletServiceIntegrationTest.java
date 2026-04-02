package com.theron.wallet.service;

import com.theron.wallet.BaseIntegrationTest;
import com.theron.wallet.TestFixtures;
import com.theron.wallet.dto.response.WalletResponse;
import com.theron.wallet.entity.Customer;
import com.theron.wallet.entity.Wallet;
import com.theron.wallet.exception.InsufficientBalanceException;
import com.theron.wallet.exception.ResourceNotFoundException;
import com.theron.wallet.repository.CustomerRepository;
import com.theron.wallet.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WalletServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private WalletService walletService;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private CustomerRepository customerRepository;

    private Customer savedCustomer;
    private Wallet savedWallet;

    @BeforeEach
    void setUp() {
        savedCustomer = customerRepository.save(TestFixtures.aCustomer());
        savedWallet = walletRepository.save(TestFixtures.aWalletWithBalance(savedCustomer, new BigDecimal("100.00")));
    }

    @Nested
    @DisplayName("credit()")
    class CreditTests {

        @Test
        @DisplayName("should increase wallet balance by the credited amount")
        void shouldCreditWalletBalance() {
            walletService.credit(savedWallet.getId(), new BigDecimal("50.00"));

            Wallet updated = walletRepository.findById(savedWallet.getId()).orElseThrow();
            assertThat(updated.getBalance()).isEqualByComparingTo(new BigDecimal("150.00"));
        }

        @Test
        @DisplayName("should credit wallet with small amounts (R$ 0.01)")
        void shouldCreditSmallAmount() {
            walletService.credit(savedWallet.getId(), new BigDecimal("0.01"));

            Wallet updated = walletRepository.findById(savedWallet.getId()).orElseThrow();
            assertThat(updated.getBalance()).isEqualByComparingTo(new BigDecimal("100.01"));
        }

        @Test
        @DisplayName("should credit wallet with large amounts")
        void shouldCreditLargeAmount() {
            walletService.credit(savedWallet.getId(), new BigDecimal("999999.99"));

            Wallet updated = walletRepository.findById(savedWallet.getId()).orElseThrow();
            assertThat(updated.getBalance()).isEqualByComparingTo(new BigDecimal("1000099.99"));
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException for non-existent wallet")
        void shouldThrowWhenWalletNotFound() {
            assertThatThrownBy(() -> walletService.credit(java.util.UUID.randomUUID(), new BigDecimal("10.00")))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Wallet not found");
        }
    }

    @Nested
    @DisplayName("debit()")
    class DebitTests {

        @Test
        @DisplayName("should decrease wallet balance by the debited amount")
        void shouldDebitWalletBalance() {
            walletService.debit(savedWallet.getId(), new BigDecimal("30.00"));

            Wallet updated = walletRepository.findById(savedWallet.getId()).orElseThrow();
            assertThat(updated.getBalance()).isEqualByComparingTo(new BigDecimal("70.00"));
        }

        @Test
        @DisplayName("should allow debiting the exact available balance")
        void shouldDebitExactBalance() {
            walletService.debit(savedWallet.getId(), new BigDecimal("100.00"));

            Wallet updated = walletRepository.findById(savedWallet.getId()).orElseThrow();
            assertThat(updated.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("should throw InsufficientBalanceException when balance is too low")
        void shouldThrowWhenInsufficientBalance() {
            assertThatThrownBy(() -> walletService.debit(savedWallet.getId(), new BigDecimal("100.01")))
                    .isInstanceOf(InsufficientBalanceException.class)
                    .hasMessageContaining("Insufficient balance");
        }

        @Test
        @DisplayName("should throw InsufficientBalanceException for zero-balance wallet")
        void shouldThrowWhenZeroBalance() {
            savedWallet.setBalance(BigDecimal.ZERO);
            walletRepository.save(savedWallet);

            assertThatThrownBy(() -> walletService.debit(savedWallet.getId(), new BigDecimal("0.01")))
                    .isInstanceOf(InsufficientBalanceException.class);
        }
    }

    @Nested
    @DisplayName("getOrCreateWallet()")
    class GetOrCreateTests {

        @Test
        @DisplayName("should return existing wallet when one exists for customer")
        void shouldReturnExistingWallet() {
            WalletResponse response = walletService.getOrCreateWallet(savedCustomer.getId());

            assertThat(response.getId()).isEqualTo(savedWallet.getId());
            assertThat(response.getBalance()).isEqualByComparingTo(new BigDecimal("100.00"));
        }

        @Test
        @DisplayName("should create a new wallet when none exists for customer")
        void shouldCreateNewWallet() {
            Customer newCustomer = customerRepository.save(Customer.builder()
                    .name("New Customer")
                    .email("new@therongroup.com")
                    .cpfCnpj("11122233344")
                    .asaasCustomerId("cus_new123")
                    .build());

            WalletResponse response = walletService.getOrCreateWallet(newCustomer.getId());

            assertThat(response.getId()).isNotNull();
            assertThat(response.getCustomerId()).isEqualTo(newCustomer.getId());
            assertThat(response.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(response.getCurrency()).isEqualTo("BRL");
        }
    }
}
