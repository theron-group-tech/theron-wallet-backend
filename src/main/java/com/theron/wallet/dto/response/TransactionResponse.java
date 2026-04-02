package com.theron.wallet.dto.response;

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
public class TransactionResponse {

    private UUID id;
    private UUID walletId;
    private TransactionType type;
    private TransactionStatus status;
    private BigDecimal amount;
    private String description;
    private String asaasPaymentId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
