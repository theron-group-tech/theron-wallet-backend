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
public class WalletResponse {

    private UUID id;
    private UUID customerId;
    private BigDecimal balance;
    private String currency;
    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
