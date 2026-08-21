package com.theron.wallet.mapper;

import com.theron.wallet.dto.response.AccountResponse;
import com.theron.wallet.dto.response.AsaasBindResponse;
import com.theron.wallet.entity.Account;

public final class AccountMapper {

    private AccountMapper() {
    }

    public static AccountResponse toResponse(Account account) {
        return toResponse(account, null);
    }

    public static AccountResponse toResponse(Account account, AsaasBindResponse bind) {
        AccountResponse.AccountResponseBuilder builder = AccountResponse.builder()
                .id(account.getId())
                .organizationId(account.getOrganization().getId())
                .ownerUserId(account.getOwnerUser() != null ? account.getOwnerUser().getId() : null)
                .name(account.getName())
                .type(account.getType())
                .status(account.getStatus())
                .currency(account.getCurrency())
                .createdAt(account.getCreatedAt())
                .updatedAt(account.getUpdatedAt());
        if (bind != null) {
            builder.asaasStatus(bind.getStatus())
                    .asaasAccountId(bind.getAsaasAccountId())
                    .asaasWalletId(bind.getAsaasWalletId())
                    .asaasMessage(bind.getMessage());
        }
        return builder.build();
    }
}
