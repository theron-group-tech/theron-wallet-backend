package com.theron.wallet.service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Minimal Account limits for Module 9 (per-operation + daily). Expanded in Module 11.
 */
public interface AccountLimitService {

    /**
     * Locks account_limit row and validates amount against max operation and daily remaining.
     * Must run inside the same DB transaction as the debit.
     */
    void assertWithinLimits(UUID accountId, BigDecimal amount);
}
