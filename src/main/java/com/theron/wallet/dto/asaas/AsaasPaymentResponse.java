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
public class AsaasPaymentResponse {

    private String id;
    private String customer;
    private String billingType;
    private BigDecimal value;
    private BigDecimal netValue;
    private String status;
    private String dueDate;
    private String description;
    private String externalReference;
    private String invoiceUrl;
    private String bankSlipUrl;
    private String transactionReceiptUrl;
    private String dateCreated;
}
