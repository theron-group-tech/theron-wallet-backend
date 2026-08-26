package com.theron.wallet.dto.request;

import com.theron.wallet.enums.PixKeyType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Create a PIX key on the Platform Account (Asaas Master). Only EVP is supported.")
public class CreatePlatformPixKeyRequest {

    @NotNull
    @Schema(description = "Only EVP (random) keys can be created via Asaas API", allowableValues = {"EVP"})
    private PixKeyType type;
}
