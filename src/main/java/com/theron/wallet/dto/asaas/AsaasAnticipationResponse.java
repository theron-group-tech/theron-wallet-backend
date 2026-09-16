package com.theron.wallet.dto.asaas;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AsaasAnticipationResponse {

    private String id;
    private String status;
    private String anticipationDate;
    private BigDecimal totalValue;
    private BigDecimal netValue;
    private BigDecimal feeValue;
    private BigDecimal value;
    private List<String> payment;
    private String object;
}
