package com.theron.wallet.service;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads Asaas {@code /finance/balance} for display and spend checks.
 * Local {@code wallet.balance} remains the ledger mirror.
 */
public interface AsaasBalanceService {

    /**
     * Asaas balance for the account's ACTIVE subaccount, or empty if unavailable.
     */
    Optional<BigDecimal> fetchAsaasBalance(UUID accountId);

    /**
     * UI balance: Asaas when available, otherwise local ledger.
     */
    BigDecimal displayBalance(UUID accountId);

    /**
     * Sum of {@link #displayBalance} for the given accounts.
     */
    BigDecimal sumDisplayBalances(Collection<UUID> accountIds);

    /**
     * Local wallet balance (ledger), or zero if missing.
     */
    BigDecimal ledgerBalance(UUID accountId);
}
