package com.theron.wallet.dto.ledger;

import com.theron.wallet.enums.LedgerTransactionType;

import java.util.List;

public record LedgerPostingRequest(
        String idempotencyKey,
        LedgerTransactionType type,
        String reference,
        List<LedgerEntryDraft> entries
) {
}
