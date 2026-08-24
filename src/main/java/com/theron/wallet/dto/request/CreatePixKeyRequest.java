package com.theron.wallet.dto.request;

import com.theron.wallet.enums.PixKeyType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePixKeyRequest {

    @NotNull(message = "type is required")
    @Schema(
            description = "Asaas API only creates EVP (random) keys",
            allowableValues = {"EVP"})
    private PixKeyType type;
}
