package com.theron.wallet.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.theron.wallet.enums.ChargeBillingType;
import com.theron.wallet.enums.ChargeSplitRole;
import com.theron.wallet.enums.ChargeStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChargeResponse {

    private UUID id;
    private UUID organizationId;
    private UUID accountId;
    private String asaasPaymentId;
    private ChargeBillingType billingType;
    private BigDecimal value;
    private BigDecimal netValue;
    private String description;
    private String externalReference;
    private LocalDate dueDate;
    private ChargeStatus status;
    private Integer installmentCount;
    private String invoiceUrl;
    private String bankSlipUrl;
    private UUID billingCustomerId;
    private String customerName;
    private String customerCpfCnpj;
    private List<SplitItem> splits;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SplitItem {
        private String walletId;
        private BigDecimal percentualValue;
        private BigDecimal fixedValue;
        private ChargeSplitRole role;
    }
}
