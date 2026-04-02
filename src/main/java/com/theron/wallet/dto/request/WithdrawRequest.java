package com.theron.wallet.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
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
public class WithdrawRequest {

    @NotNull(message = "Customer ID is required")
    private UUID customerId;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be at least R$ 0.01")
    private BigDecimal amount;

    @NotBlank(message = "PIX address key is required")
    @Size(max = 100, message = "PIX address key must be at most 100 characters")
    private String pixAddressKey;

    @NotBlank(message = "PIX address key type is required (CPF, CNPJ, EMAIL, PHONE, EVP)")
    @Size(max = 10, message = "PIX address key type must be at most 10 characters")
    private String pixAddressKeyType;

    @Size(max = 255, message = "Description must be at most 255 characters")
    private String description;

    @Size(max = 100, message = "Idempotency key must be at most 100 characters")
    private String idempotencyKey;
}
