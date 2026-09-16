package com.theron.wallet.domain.onboarding;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AsaasOnboardingPayload {

    private String name;
    private String cpfCnpj;
    private String birthDate;
    private String email;
    private String mobilePhone;
    private String tradeName;
    private String companyType;
    private String address;
    private String addressNumber;
    private String complement;
    private String province;
    private String postalCode;
    private String city;
    private BigDecimal incomeValue;
}
