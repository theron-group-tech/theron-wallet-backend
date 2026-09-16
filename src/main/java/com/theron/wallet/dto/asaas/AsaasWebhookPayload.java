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
public class AsaasWebhookPayload {

    private String id;
    private String event;
    private Account account;
    private Payment payment;
    private Transfer transfer;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Account {
        private String id;
        private String ownerId;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Payment {
        private String id;
        private String customer;
        private String billingType;
        private BigDecimal value;
        private BigDecimal netValue;
        private String status;
        private String dueDate;
        private String paymentDate;
        private String externalReference;
        private String description;
        private String pixQrCodeId;
        private Object pixTransaction;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Transfer {
        private String id;
        private BigDecimal value;
        private BigDecimal netValue;
        private String status;
        private String type;
        private String operationType;
        private String description;
        private String externalReference;
        private String effectiveDate;
        private Boolean authorized;
        private String failReason;
        private String transactionReceiptUrl;
    }
}
