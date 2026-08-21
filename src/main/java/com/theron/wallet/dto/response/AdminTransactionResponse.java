package com.theron.wallet.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.theron.wallet.enums.TransactionStatus;
import com.theron.wallet.enums.TransactionType;
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
public class AdminTransactionResponse {

    private UUID id;
    private UUID organizationId;
    private String organizationName;
    private UUID accountId;
    private String accountName;
    private String ownerName;
    private UUID walletId;
    private TransactionType type;
    private TransactionStatus status;
    private BigDecimal amount;
    private String currency;
    private String reference;
    private String description;
    private String counterpartHint;
    private String asaasPaymentId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime completedAt;
}
