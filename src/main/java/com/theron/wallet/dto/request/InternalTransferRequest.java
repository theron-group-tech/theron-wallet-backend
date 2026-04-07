package com.theron.wallet.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InternalTransferRequest {

    @Size(max = 100, message = "Idempotency key must be at most 100 characters")
    private String idempotencyKey;

    @NotNull(message = "Sender subaccount ID is required")
    private UUID senderSubaccountId;

    @NotNull(message = "Receiver subaccount ID is required")
    private UUID receiverSubaccountId;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be at least R$ 0.01")
    private BigDecimal amount;

    @Size(max = 255, message = "Description must be at most 255 characters")
    private String description;
}
