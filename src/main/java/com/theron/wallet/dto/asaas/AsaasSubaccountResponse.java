package com.theron.wallet.dto.asaas;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
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
public class AsaasSubaccountResponse {

    @JsonProperty("object")
    private String objectType;

    private String id;
    private String name;
    private String email;
    private String loginEmail;
    private String cpfCnpj;
    private String mobilePhone;
    private String phone;
    private String address;
    private String addressNumber;
    private String complement;
    private String province;
    private String postalCode;
    private BigDecimal incomeValue;
    private String personType;
    private String companyType;
    private String tradingName;
    private String site;
    private Object city;       // Can be cityId (int) or city name (String)
    private String state;
    private String country;
    private String walletId;
    private String apiKey;
    private AsaasAccountNumber accountNumber;
    private String dateCreated;
}
