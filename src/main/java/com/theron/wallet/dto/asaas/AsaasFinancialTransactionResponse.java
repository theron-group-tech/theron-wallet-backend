package com.theron.wallet.dto.asaas;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AsaasFinancialTransactionResponse {

    private String id;
    private String type;
    private BigDecimal value;
    private LocalDate date;
    private String paymentId;
    private String transferId;
    private String anticipationId;
    private String billId;
    private String description;
    private String balanceType;
}
