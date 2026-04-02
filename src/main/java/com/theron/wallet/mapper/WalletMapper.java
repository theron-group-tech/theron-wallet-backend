package com.theron.wallet.mapper;

import com.theron.wallet.dto.response.WalletResponse;
import com.theron.wallet.entity.Wallet;

public final class WalletMapper {

    private WalletMapper() {
    }

    public static WalletResponse toResponse(Wallet entity) {
        return WalletResponse.builder()
                .id(entity.getId())
                .customerId(entity.getCustomer().getId())
                .balance(entity.getBalance())
                .currency(entity.getCurrency())
                .active(entity.getActive())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
