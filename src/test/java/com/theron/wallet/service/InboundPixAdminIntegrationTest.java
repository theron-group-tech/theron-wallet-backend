package com.theron.wallet.service;

import com.theron.wallet.BaseIntegrationTest;
import com.theron.wallet.TestFixtures;
import com.theron.wallet.dto.asaas.AsaasListResponse;
import com.theron.wallet.dto.asaas.AsaasPaymentResponse;
import com.theron.wallet.dto.asaas.AsaasWebhookConfigResponse;
import com.theron.wallet.dto.response.InboundReconcileResponse;
import com.theron.wallet.dto.response.SubaccountWebhookRepairResponse;
import com.theron.wallet.entity.Account;
import com.theron.wallet.entity.Organization;
import com.theron.wallet.entity.Subaccount;
import com.theron.wallet.entity.Wallet;
import com.theron.wallet.enums.AccountStatus;
import com.theron.wallet.enums.AccountType;
import com.theron.wallet.enums.DocumentType;
import com.theron.wallet.enums.OrganizationStatus;
import com.theron.wallet.enums.SubaccountStatus;
import com.theron.wallet.enums.TransactionType;
import com.theron.wallet.repository.AccountRepository;
import com.theron.wallet.repository.OrganizationRepository;
import com.theron.wallet.repository.SubaccountRepository;
import com.theron.wallet.repository.TransactionRepository;
import com.theron.wallet.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InboundPixAdminIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private InboundPixReconcileService inboundPixReconcileService;

    @Autowired
    private AsaasSubaccountWebhookService asaasSubaccountWebhookService;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private SubaccountRepository subaccountRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    private Account account;
    private Subaccount subaccount;
    private Wallet wallet;

    @BeforeEach
    void setUpAccount() {
        Organization organization = organizationRepository.save(Organization.builder()
                .legalName("Reconcile Org")
                .document(uniqueDigits(14))
                .documentType(DocumentType.CNPJ)
                .status(OrganizationStatus.ACTIVE)
                .build());
        account = accountRepository.save(Account.builder()
                .organization(organization)
                .name("Marcelo")
                .type(AccountType.EMPLOYEE)
                .status(AccountStatus.ACTIVE)
                .currency("BRL")
                .build());
        wallet = walletRepository.save(Wallet.builder()
                .account(account)
                .balance(BigDecimal.ZERO)
                .currency("BRL")
                .active(true)
                .build());
        subaccount = TestFixtures.aSubaccount(uniqueDigits(14), SubaccountStatus.ACTIVE);
        subaccount.setAccount(account);
        subaccount.setEncryptedApiKey(new byte[]{1, 2, 3, 4, 5, 6, 7, 8});
        subaccount.setWebhookToken("whsec_" + UUID.randomUUID());
        subaccount = subaccountRepository.save(subaccount);

        when(asaasApiKeyResolver.resolveForSubaccount(subaccount.getId())).thenReturn("sub-api-key");
    }

    @Test
    @DisplayName("reconcile credits orphan RECEIVED payment and is idempotent")
    void reconcileCreditsOrphanPayment() {
        String paymentId = "pay_orphan_" + UUID.randomUUID().toString().substring(0, 12);
        when(asaasPaymentClient.listPayments(anyString(), eq("RECEIVED"), anyInt(), anyInt()))
                .thenReturn(AsaasListResponse.<AsaasPaymentResponse>builder()
                        .hasMore(false)
                        .data(List.of(AsaasPaymentResponse.builder()
                                .id(paymentId)
                                .value(new BigDecimal("100.00"))
                                .status("RECEIVED")
                                .description("Cobrar orphan")
                                .build()))
                        .build())
                .thenReturn(AsaasListResponse.<AsaasPaymentResponse>builder()
                        .hasMore(false)
                        .data(List.of(AsaasPaymentResponse.builder()
                                .id(paymentId)
                                .value(new BigDecimal("100.00"))
                                .status("RECEIVED")
                                .build()))
                        .build());
        when(asaasPaymentClient.listPayments(anyString(), eq("CONFIRMED"), anyInt(), anyInt()))
                .thenReturn(AsaasListResponse.<AsaasPaymentResponse>builder()
                        .hasMore(false)
                        .data(List.of())
                        .build());

        InboundReconcileResponse first = inboundPixReconcileService.reconcileAccount(account.getId());
        assertThat(first.getCredited()).isEqualTo(1);
        assertThat(walletRepository.findById(wallet.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo("100.00");
        assertThat(transactionRepository.findByAsaasPaymentId(paymentId)).isPresent()
                .hasValueSatisfying(tx -> assertThat(tx.getType()).isEqualTo(TransactionType.TRANSFER_IN));

        InboundReconcileResponse second = inboundPixReconcileService.reconcileAccount(account.getId());
        assertThat(second.getCredited()).isZero();
        assertThat(second.getSkipped()).isGreaterThanOrEqualTo(1);
        assertThat(walletRepository.findById(wallet.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("repair registers Asaas webhook with subaccount webhookToken")
    void repairRegistersWebhook() {
        when(asaasWebhookClient.createWebhook(eq("sub-api-key"), any()))
                .thenReturn(AsaasWebhookConfigResponse.builder().id("wh_123").build());

        SubaccountWebhookRepairResponse response =
                asaasSubaccountWebhookService.repairForSubaccount(subaccount.getId());

        assertThat(response.getSucceeded()).isEqualTo(1);
        assertThat(response.getAsaasWebhookId()).isEqualTo("wh_123");
        verify(asaasWebhookClient).createWebhook(eq("sub-api-key"), any());
    }

    private static String uniqueDigits(int length) {
        String digits = String.valueOf(Math.abs(UUID.randomUUID().getMostSignificantBits()));
        if (digits.length() >= length) {
            return digits.substring(0, length);
        }
        return digits + "0".repeat(length - digits.length());
    }
}
