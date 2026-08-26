package com.theron.wallet.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.theron.wallet.enums.PixKeyType;
import com.theron.wallet.enums.TransactionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PlatformPixTransferResponse {

    /** Asaas transfer id (not a Theron UUID). */
    private String id;
    private BigDecimal amount;
    private TransactionStatus status;
    private String destinationPixKey;
    private PixKeyType destinationPixKeyType;
    private String providerReference;
    private String description;
    private String createdAt;
}
