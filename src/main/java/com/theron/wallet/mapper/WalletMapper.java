package com.theron.wallet.mapper;

import com.theron.wallet.dto.response.WalletResponse;
import com.theron.wallet.entity.Wallet;

public final class WalletMapper {

    private WalletMapper() {
    }

    public static WalletResponse toResponse(Wallet entity) {
        return toResponse(entity, entity.getBalance(), entity.getBalance(), null);
    }

    public static WalletResponse toResponse(
            Wallet entity,
            java.math.BigDecimal displayBalance,
            java.math.BigDecimal ledgerBalance,
            Boolean asaasBalanceUnavailable) {
        return WalletResponse.builder()
                .id(entity.getId())
                .subaccountId(entity.getSubaccount() != null ? entity.getSubaccount().getId() : null)
                .accountId(entity.getAccount() != null ? entity.getAccount().getId() : null)
                .balance(displayBalance != null ? displayBalance : entity.getBalance())
                .ledgerBalance(ledgerBalance != null ? ledgerBalance : entity.getBalance())
                .asaasBalanceUnavailable(asaasBalanceUnavailable)
                .currency(entity.getCurrency())
                .active(entity.getActive())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
