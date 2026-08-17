package com.theron.wallet.service;

import com.theron.wallet.enums.LimitTransactionType;

import java.math.BigDecimal;
import java.util.UUID;

public record LimitContext(
        UUID organizationId,
        UUID accountId,
        UUID actorUserId,
        LimitTransactionType transactionType,
        BigDecimal amount,
        UUID excludeTransactionId
) {
}
