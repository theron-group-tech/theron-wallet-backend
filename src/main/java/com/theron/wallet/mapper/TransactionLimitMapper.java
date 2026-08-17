package com.theron.wallet.mapper;

import com.theron.wallet.dto.response.TransactionLimitResponse;
import com.theron.wallet.entity.TransactionLimit;

public final class TransactionLimitMapper {

    private TransactionLimitMapper() {
    }

    public static TransactionLimitResponse toResponse(TransactionLimit limit) {
        return TransactionLimitResponse.builder()
                .id(limit.getId())
                .organizationId(limit.getOrganization().getId())
                .accountId(limit.getAccount() != null ? limit.getAccount().getId() : null)
                .userId(limit.getUser() != null ? limit.getUser().getId() : null)
                .roleId(limit.getRole() != null ? limit.getRole().getId() : null)
                .transactionType(limit.getTransactionType())
                .period(limit.getPeriod())
                .maxAmount(limit.getMaxAmount())
                .enabled(limit.isEnabled())
                .createdAt(limit.getCreatedAt())
                .updatedAt(limit.getUpdatedAt())
                .build();
    }
}
