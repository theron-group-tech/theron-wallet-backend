package com.theron.wallet.dto.asaas;

import com.fasterxml.jackson.annotation.JsonInclude;
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
public class AsaasTransferRequest {

    private BigDecimal value;
    private String pixAddressKey;
    private String pixAddressKeyType;
    private String operationType;
    private String description;
    private String externalReference;
}
