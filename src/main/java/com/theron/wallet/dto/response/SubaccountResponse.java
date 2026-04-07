package com.theron.wallet.dto.response;

import com.theron.wallet.enums.SubaccountStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubaccountResponse {

    private UUID id;

    @Schema(description = "Owner full name")
    private String name;

    @Schema(description = "Owner email")
    private String email;

    @Schema(description = "Login email — may differ from email if loginEmail was provided")
    private String loginEmail;

    @Schema(description = "CPF or CNPJ of the subaccount owner")
    private String cpfCnpj;

    @Schema(description = "Person type derived from cpfCnpj: FISICA (CPF, 11 digits) or JURIDICA (CNPJ, 14 digits)")
    private String personType;

    @Schema(description = "Mobile phone")
    private String mobilePhone;

    @Schema(description = "Landline phone")
    private String phone;

    @Schema(description = "Date of birth — relevant for Pessoa Física")
    private String birthDate;

    @Schema(description = "Company type — relevant for Pessoa Jurídica")
    private String companyType;

    @Schema(description = "Asaas account ID assigned after provisioning")
    private String asaasAccountId;

    @Schema(description = "Asaas wallet ID assigned after provisioning")
    private String asaasWalletId;

    private SubaccountStatus status;
    private String statusDescription;
    private String statusReason;

    private BigDecimal incomeValue;
    private String address;
    private String addressNumber;
    private String complement;
    private String province;
    private String postalCode;


    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
