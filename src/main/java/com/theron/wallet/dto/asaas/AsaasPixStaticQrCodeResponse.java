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
public class AsaasPixStaticQrCodeResponse {
    private String id;
    private String addressKey;
    private String description;
    private String payload;
    private String encodedImage;
    private String expirationDate;
    private Boolean allowsMultiplePayments;
    private BigDecimal value;
    private String format;
}
