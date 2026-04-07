package com.theron.wallet.dto.asaas;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request body for POST /v3/customers using the subaccount's own API key.
 * Each subaccount manages its own customers in isolation.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AsaasCreateCustomerRequest {

    private String name;
    private String email;
    private String cpfCnpj;
    private String mobilePhone;
    private String phone;

    @Builder.Default
    private Boolean notificationDisabled = true;
}

