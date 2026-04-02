package com.theron.wallet.dto.response;

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
public class DepositResponse {

    private UUID transactionId;
    private UUID walletId;
    private BigDecimal amount;
    private String status;
    private String asaasPaymentId;
    private String description;
    private LocalDateTime createdAt;
}
