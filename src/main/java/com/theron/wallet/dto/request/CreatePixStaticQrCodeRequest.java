package com.theron.wallet.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.*;
import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePixStaticQrCodeRequest {

    // Opcional: null = QR Code com valor aberto (pagador define o valor)
    @DecimalMin(value = "0.01", message = "value must be at least 0.01")
    private BigDecimal value;

    @Size(max = 140, message = "description must be at most 140 characters")
    private String description;
}
