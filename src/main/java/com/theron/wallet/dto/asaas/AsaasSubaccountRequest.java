package com.theron.wallet.dto.asaas;

import com.fasterxml.jackson.annotation.JsonInclude;
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
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AsaasSubaccountRequest {

    private String name;
    private String email;
    private String cpfCnpj;
    private String mobilePhone;
    private BigDecimal incomeValue;
    private String address;
    private String addressNumber;
    private String complement;
    private String province;
    private String postalCode;
    private String phone;
    private String loginEmail;
    private String companyType;
}
