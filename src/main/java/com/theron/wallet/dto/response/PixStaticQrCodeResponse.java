package com.theron.wallet.dto.response;

import lombok.*;
import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PixStaticQrCodeResponse {
    private String id;
    private String addressKey;
    private String description;
    private String payload;
    private String encodedImage;
    private String expirationDate;
    private Boolean allowsMultiplePayments;
    private BigDecimal value;
}
