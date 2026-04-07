package com.theron.wallet.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubaccountApiKeyRequest {

    @NotBlank(message = "apiKey is required")
    @Schema(
            description = "Raw Asaas API key for the subaccount. Obtained from the Asaas dashboard "
                    + "or subaccount creation response. Will be encrypted and stored — never returned.",
            example = "$aact_hmlg_xxxxx"
    )
    private String apiKey;
}

