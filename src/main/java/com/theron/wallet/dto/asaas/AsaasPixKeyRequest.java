package com.theron.wallet.dto.asaas;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AsaasPixKeyRequest {
    private String type; // Asaas create: EVP only. Response/list may include CPF, CNPJ, EMAIL, PHONE.
}
