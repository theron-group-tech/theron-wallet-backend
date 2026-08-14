package com.theron.wallet.dto.ledger;

import com.theron.wallet.enums.LedgerDirection;

import java.math.BigDecimal;
import java.util.UUID;

public record LedgerEntryDraft(
        UUID ledgerAccountId,
        LedgerDirection direction,
        BigDecimal amount
) {
}
