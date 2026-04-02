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
public class AsaasSubaccountResponse {

    private String id;
    private String name;
    private String email;
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
    private String city;
    private String state;
    private String country;
    private String walletId;
    private String apiKey;
    private String accountNumber;
    private String dateCreated;
}
