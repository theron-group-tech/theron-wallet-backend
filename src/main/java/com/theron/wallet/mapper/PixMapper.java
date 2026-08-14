package com.theron.wallet.mapper;

import com.theron.wallet.dto.response.AccountPixKeyResponse;
import com.theron.wallet.dto.response.PixTransferResponse;
import com.theron.wallet.entity.PixKey;
import com.theron.wallet.entity.PixTransaction;

public final class PixMapper {

    private PixMapper() {
    }

    public static AccountPixKeyResponse toKeyResponse(PixKey pixKey) {
        return AccountPixKeyResponse.builder()
                .id(pixKey.getId())
                .accountId(pixKey.getAccount().getId())
                .type(pixKey.getType())
                .key(pixKey.getKey())
                .status(pixKey.getStatus())
                .createdAt(pixKey.getCreatedAt())
                .build();
    }

    public static PixTransferResponse toTransferResponse(PixTransaction pixTransaction) {
        return PixTransferResponse.builder()
                .id(pixTransaction.getId())
                .transactionId(pixTransaction.getTransaction().getId())
                .accountId(pixTransaction.getAccount().getId())
                .pixKeyId(pixTransaction.getPixKey() != null ? pixTransaction.getPixKey().getId() : null)
                .amount(pixTransaction.getTransaction().getAmount())
                .status(pixTransaction.getStatus())
                .destinationPixKey(pixTransaction.getDestinationPixKey())
                .destinationPixKeyType(pixTransaction.getDestinationPixKeyType())
                .providerReference(pixTransaction.getProviderReference())
                .description(pixTransaction.getTransaction().getDescription())
                .createdAt(pixTransaction.getCreatedAt())
                .updatedAt(pixTransaction.getUpdatedAt())
                .build();
    }
}
