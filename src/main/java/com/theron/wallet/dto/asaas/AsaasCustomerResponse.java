package com.theron.wallet.dto.asaas;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AsaasCustomerResponse {

    private String id;
    private String name;
    private String email;
    private String cpfCnpj;
    private String phone;
    private String mobilePhone;
    private String personType;
    private Boolean notificationDisabled;
    private String city;
    private String state;
    private String country;
    private String externalReference;
    private String dateCreated;
}
