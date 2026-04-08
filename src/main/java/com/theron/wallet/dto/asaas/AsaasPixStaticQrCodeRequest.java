package com.theron.wallet.dto.asaas;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AsaasPixStaticQrCodeRequest {
    private BigDecimal value;     // null = QR Code aberto (qualquer valor)
    private String description;
    private String format;         // IMAGE | PAYLOAD | ALL
    private Integer expirationSeconds;
}
