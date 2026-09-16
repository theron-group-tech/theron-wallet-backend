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
public class AsaasPixTransactionResponse {

    private String id;
    private String status;
    private BigDecimal value;
    private String description;
    private String endToEndIdentifier;
    private String transferId;
    private String refusalReason;
    private ExternalAccount externalAccount;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ExternalAccount {
        private String name;
        private String cpfCnpj;
        private String ispbName;
    }
}
