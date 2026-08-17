package com.theron.wallet.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.theron.wallet.enums.LimitPeriod;
import com.theron.wallet.enums.LimitTransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TransactionLimitResponse {

    private UUID id;
    private UUID organizationId;
    private UUID accountId;
    private UUID userId;
    private UUID roleId;
    private LimitTransactionType transactionType;
    private LimitPeriod period;
    private BigDecimal maxAmount;
    private boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
