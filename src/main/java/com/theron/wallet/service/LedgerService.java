package com.theron.wallet.service;

import com.theron.wallet.dto.ledger.LedgerPostingRequest;
import com.theron.wallet.dto.response.LedgerBalanceResponse;
import com.theron.wallet.dto.response.LedgerTransactionResponse;
import com.theron.wallet.entity.Account;
import com.theron.wallet.entity.Transaction;

import java.math.BigDecimal;
import java.util.UUID;

public interface LedgerService {

    void provisionForAccount(Account account);

    LedgerTransactionResponse post(LedgerPostingRequest request);

    LedgerTransactionResponse postCredit(UUID accountId, BigDecimal amount, String idempotencyKey, String reference);

    LedgerTransactionResponse postDebit(UUID accountId, BigDecimal amount, String idempotencyKey, String reference);

    LedgerTransactionResponse postTransfer(
            UUID fromAccountId, UUID toAccountId, BigDecimal amount, String idempotencyKey, String reference);

    /**
     * Convenience overload for callers that already have the transfer transactions.
     * The ledger is account-based, so only their wallet account IDs are required.
     */
    default LedgerTransactionResponse postTransfer(
            Transaction fromTransaction, Transaction toTransaction, BigDecimal amount,
            String currency, String reference) {
        UUID fromAccountId = fromTransaction.getWallet().getAccount().getId();
        UUID toAccountId = toTransaction.getWallet().getAccount().getId();
        String idempotencyKey = "payment-order:" + reference;
        return postTransfer(fromAccountId, toAccountId, amount, idempotencyKey, reference);
    }

    BigDecimal reconstructBalance(UUID accountId);

    LedgerBalanceResponse getBalance(UUID accountId);
}
