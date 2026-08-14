package com.theron.wallet.dto.request;

import com.theron.wallet.enums.PixKeyType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Create a PIX key for an Account (synced with Asaas)")
public class CreateAccountPixKeyRequest {

    @NotNull
    private UUID accountId;

    @NotNull
    @Schema(allowableValues = {"CPF", "CNPJ", "EMAIL", "PHONE", "EVP"})
    private PixKeyType type;
}
