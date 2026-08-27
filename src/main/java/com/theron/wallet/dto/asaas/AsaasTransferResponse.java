package com.theron.wallet.dto.asaas;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
@JsonIgnoreProperties(ignoreUnknown = true)
public class AsaasTransferResponse {

    private String id;
    private BigDecimal value;
    private BigDecimal netValue;
    private String status;
    private String operationType;
    private String description;
    private String scheduleDate;
    private BigDecimal transferFee;
    private String pixAddressKey;
    private String pixAddressKeyType;
    private String externalReference;
    private String dateCreated;
    private String walletId;
    private String failReason;
    private String effectiveDate;
}
