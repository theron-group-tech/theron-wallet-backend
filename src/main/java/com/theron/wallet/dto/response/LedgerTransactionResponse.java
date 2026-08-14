package com.theron.wallet.dto.response;

import com.theron.wallet.enums.LedgerDirection;
import com.theron.wallet.enums.LedgerTransactionStatus;
import com.theron.wallet.enums.LedgerTransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerTransactionResponse {

    private UUID id;
    private String reference;
    private LedgerTransactionType type;
    private LedgerTransactionStatus status;
    private String idempotencyKey;
    private LocalDateTime createdAt;
    private List<Entry> entries;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Entry {
        private UUID id;
        private UUID ledgerAccountId;
        private LedgerDirection direction;
        private BigDecimal amount;
    }
}
