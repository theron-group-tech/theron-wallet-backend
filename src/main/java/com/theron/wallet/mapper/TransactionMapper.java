package com.theron.wallet.mapper;

import com.theron.wallet.dto.response.DepositResponse;
import com.theron.wallet.dto.response.InternalTransferResponse;
import com.theron.wallet.dto.response.TransactionResponse;
import com.theron.wallet.dto.response.WithdrawResponse;
import com.theron.wallet.entity.Transaction;

public final class TransactionMapper {

    private TransactionMapper() {
    }

    public static TransactionResponse toResponse(Transaction entity) {
        return TransactionResponse.builder()
                .id(entity.getId())
                .walletId(entity.getWallet().getId())
                .organizationId(entity.getOrganization() != null ? entity.getOrganization().getId() : null)
                .accountId(entity.getAccount() != null ? entity.getAccount().getId() : null)
                .type(entity.getType())
                .status(entity.getStatus())
                .amount(entity.getAmount())
                .currency(entity.getCurrency())
                .reference(entity.getReference())
                .description(entity.getDescription())
                .asaasPaymentId(entity.getAsaasPaymentId())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .completedAt(entity.getCompletedAt())
                .build();
    }

    public static DepositResponse toDepositResponse(Transaction entity) {
        return DepositResponse.builder()
                .transactionId(entity.getId())
                .walletId(entity.getWallet().getId())
                .amount(entity.getAmount())
                .status(entity.getStatus().name())
                .asaasPaymentId(entity.getAsaasPaymentId())
                .description(entity.getDescription())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    public static WithdrawResponse toWithdrawResponse(Transaction entity) {
        return WithdrawResponse.builder()
                .transactionId(entity.getId())
                .walletId(entity.getWallet().getId())
                .amount(entity.getAmount())
                .status(entity.getStatus().name())
                .asaasTransferId(entity.getAsaasPaymentId())
                .description(entity.getDescription())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    /**
     * Builds an {@link InternalTransferResponse} from the sender and receiver transaction pair.
     * The sender transaction carries the client-controlled idempotency key; the receiver transaction
     * is linked via {@code externalReference = senderTx.getId()}.
     */
    public static InternalTransferResponse toInternalTransferResponse(Transaction senderTx, Transaction receiverTx) {
        return InternalTransferResponse.builder()
                .senderTransactionId(senderTx.getId())
                .receiverTransactionId(receiverTx.getId())
                .senderWalletId(senderTx.getWallet().getId())
                .receiverWalletId(receiverTx.getWallet().getId())
                .amount(senderTx.getAmount())
                .status(senderTx.getStatus().name())
                .description(senderTx.getDescription())
                .idempotencyKey(senderTx.getIdempotencyKey())
                .createdAt(senderTx.getCreatedAt())
                .build();
    }
}
