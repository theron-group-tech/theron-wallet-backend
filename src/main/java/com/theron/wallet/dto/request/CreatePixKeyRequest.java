package com.theron.wallet.dto.request;

import com.theron.wallet.enums.PixKeyType;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePixKeyRequest {

    @NotNull(message = "type is required")
    private PixKeyType type; // CPF, CNPJ, EMAIL, PHONE, EVP
}
