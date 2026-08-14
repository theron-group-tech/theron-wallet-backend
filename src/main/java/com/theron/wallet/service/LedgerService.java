package com.theron.wallet.service;

import com.theron.wallet.dto.ledger.LedgerPostingRequest;
import com.theron.wallet.dto.response.LedgerBalanceResponse;
import com.theron.wallet.dto.response.LedgerTransactionResponse;
import com.theron.wallet.entity.Account;

import java.math.BigDecimal;
import java.util.UUID;

public interface LedgerService {

    void provisionForAccount(Account account);

    LedgerTransactionResponse post(LedgerPostingRequest request);

    LedgerTransactionResponse postCredit(UUID accountId, BigDecimal amount, String idempotencyKey, String reference);

    LedgerTransactionResponse postDebit(UUID accountId, BigDecimal amount, String idempotencyKey, String reference);

    LedgerTransactionResponse postTransfer(
            UUID fromAccountId, UUID toAccountId, BigDecimal amount, String idempotencyKey, String reference);

    BigDecimal reconstructBalance(UUID accountId);

    LedgerBalanceResponse getBalance(UUID accountId);
}
