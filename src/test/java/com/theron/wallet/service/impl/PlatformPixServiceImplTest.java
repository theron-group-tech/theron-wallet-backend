package com.theron.wallet.service.impl;

import com.theron.wallet.config.AsaasProperties;
import com.theron.wallet.dto.asaas.AsaasPixStaticQrCodeResponse;
import com.theron.wallet.dto.request.CreatePlatformPixQrCodeRequest;
import com.theron.wallet.dto.response.AccountPixQrCodeResponse;
import com.theron.wallet.entity.Account;
import com.theron.wallet.entity.Organization;
import com.theron.wallet.entity.PixKey;
import com.theron.wallet.entity.PlatformPixTransfer;
import com.theron.wallet.entity.Transaction;
import com.theron.wallet.entity.Wallet;
import com.theron.wallet.enums.PixKeyStatus;
import com.theron.wallet.enums.TransactionStatus;
import com.theron.wallet.integration.AsaasPixClient;
import com.theron.wallet.integration.AsaasTransferClient;
import com.theron.wallet.repository.PixKeyRepository;
import com.theron.wallet.repository.PlatformPixTransferRepository;
import com.theron.wallet.repository.TransactionRepository;
import com.theron.wallet.repository.WalletRepository;
import com.theron.wallet.service.NotificationService;
import com.theron.wallet.service.TransactionLifecycleService;
import com.theron.wallet.service.WalletService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformPixServiceImplTest {

    @Mock private AsaasPixClient asaasPixClient;
    @Mock private AsaasTransferClient asaasTransferClient;
    @Mock private AsaasProperties asaasProperties;
    @Mock private PlatformPixTransferRepository platformPixTransferRepository;
    @Mock private PixKeyRepository pixKeyRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private WalletRepository walletRepository;
    @Mock private WalletService walletService;
    @Mock private NotificationService notificationService;
    @Mock private TransactionLifecycleService transactionLifecycleService;
    @Mock private PlatformTransactionManager transactionManager;

    @InjectMocks private PlatformPixServiceImpl service;

    @Test
    void creditsCompletedInternalDestinationOnlyOnce() {
        UUID accountId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();

        Organization organization = new Organization();
        organization.setId(organizationId);
        Account account = new Account();
        account.setId(accountId);
        account.setOrganization(organization);
        Wallet wallet = Wallet.builder().id(walletId).account(account).build();
        PixKey pixKey = PixKey.builder()
                .account(account)
                .organization(organization)
                .key("internal-key")
                .status(PixKeyStatus.ACTIVE)
                .build();
        PlatformPixTransfer row = PlatformPixTransfer.builder()
                .id(UUID.randomUUID())
                .asaasTransferId("transfer-master-1")
                .amount(new BigDecimal("42.50"))
                .status(TransactionStatus.PROCESSING)
                .destinationPixKey("internal-key")
                .idempotencyKey("platform-request-1")
                .build();

        when(platformPixTransferRepository.findByAsaasTransferIdForUpdate("transfer-master-1"))
                .thenReturn(Optional.of(row));
        when(pixKeyRepository.findByKeyAndStatus("internal-key", PixKeyStatus.ACTIVE))
                .thenReturn(Optional.of(pixKey));
        when(transactionRepository.findByAsaasPaymentId("transfer-master-1"))
                .thenReturn(Optional.empty());
        when(transactionRepository.findByIdempotencyKey("asaas:platform-pix:in:transfer-master-1"))
                .thenReturn(Optional.empty());
        when(walletRepository.findByAccountIdWithLock(accountId)).thenReturn(Optional.of(wallet));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction transaction = invocation.getArgument(0);
            transaction.setId(transactionId);
            return transaction;
        });
        when(transactionLifecycleService.transition(any(Transaction.class), eq(TransactionStatus.COMPLETED)))
                .thenAnswer(invocation -> {
                    Transaction transaction = invocation.getArgument(0);
                    transaction.setStatus(TransactionStatus.COMPLETED);
                    return transaction;
                });

        service.applyWebhookStatus("transfer-master-1", "TRANSFER_DONE");
        service.applyWebhookStatus("transfer-master-1", "TRANSFER_DONE");

        assertThat(row.getCreditTransactionId()).isEqualTo(transactionId);
        verify(walletService, times(1)).credit(walletId, new BigDecimal("42.50"));
        verify(notificationService, times(1)).notifyUsersWithPermission(
                eq(organizationId), any(), any(), any(), eq(transactionId), any());
    }

    @Test
    void createQrCodeUsesMasterApiKeyAndReturnsPayload() {
        when(asaasProperties.getKey()).thenReturn("master-api-key");

        when(asaasPixClient.createStaticQrCode(
                eq("master-api-key"),
                eq("pix-key-asaas-1"),
                any()))
                .thenReturn(AsaasPixStaticQrCodeResponse.builder()
                        .payload("00020126...")
                        .encodedImage("data:image/png;base64,abc")
                        .value(new BigDecimal("25.00"))
                        .description("Cobrança teste")
                        .build());

        AccountPixQrCodeResponse response = service.createQrCode(CreatePlatformPixQrCodeRequest.builder()
                .pixKeyId("pix-key-asaas-1")
                .value(new BigDecimal("25.00"))
                .description("Cobrança teste")
                .build());

        assertThat(response.getPayload()).isEqualTo("00020126...");
        assertThat(response.getEncodedImage()).isEqualTo("data:image/png;base64,abc");
        assertThat(response.getValue()).isEqualByComparingTo(new BigDecimal("25.00"));
        assertThat(response.getDescription()).isEqualTo("Cobrança teste");
        verify(asaasPixClient).createStaticQrCode(eq("master-api-key"), eq("pix-key-asaas-1"), any());
    }
}
