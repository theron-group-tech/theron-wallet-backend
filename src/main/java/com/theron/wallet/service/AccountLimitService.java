package com.theron.wallet.service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Minimal Account limits for Module 9 (per-operation + daily). Expanded in Module 11.
 */
public interface AccountLimitService {

    /**
     * Ensures an {@code account_limit} row exists (creates defaults if missing).
     */
    void ensureDefaults(UUID accountId);

    /**
     * Locks account_limit row and validates amount against max operation and daily remaining.
     * Must run inside the same DB transaction as the debit.
     * Missing rows are auto-provisioned with defaults.
     */
    void assertWithinLimits(UUID accountId, BigDecimal amount);

    /**
     * Same as {@link #assertWithinLimits(UUID, BigDecimal)} but excludes {@code excludeTransactionId}
     * from the daily SUM (PIX hold already counted as PENDING_APPROVAL).
     */
    void assertWithinLimits(UUID accountId, BigDecimal amount, UUID excludeTransactionId);
}
