package com.theron.wallet.mapper;

import com.theron.wallet.dto.asaas.AsaasCustomerRequest;
import com.theron.wallet.dto.request.CustomerRequest;
import com.theron.wallet.dto.response.CustomerResponse;
import com.theron.wallet.entity.Customer;

public final class CustomerMapper {

    private CustomerMapper() {
    }

    public static Customer toEntity(CustomerRequest request) {
        return Customer.builder()
                .name(request.getName())
                .email(request.getEmail())
                .cpfCnpj(request.getCpfCnpj())
                .phone(request.getPhone())
                .mobilePhone(request.getMobilePhone())
                .build();
    }

    public static CustomerResponse toResponse(Customer entity) {
        return CustomerResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .email(entity.getEmail())
                .cpfCnpj(entity.getCpfCnpj())
                .phone(entity.getPhone())
                .mobilePhone(entity.getMobilePhone())
                .asaasCustomerId(entity.getAsaasCustomerId())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public static AsaasCustomerRequest toAsaasRequest(Customer entity) {
        return AsaasCustomerRequest.builder()
                .name(entity.getName())
                .email(entity.getEmail())
                .cpfCnpj(entity.getCpfCnpj())
                .phone(entity.getPhone())
                .mobilePhone(entity.getMobilePhone())
                .notificationDisabled(entity.getNotificationDisabled())
                .externalReference(entity.getId() != null ? entity.getId().toString() : null)
                .build();
    }

    public static void updateEntity(Customer entity, CustomerRequest request) {
        entity.setName(request.getName());
        entity.setEmail(request.getEmail());
        entity.setPhone(request.getPhone());
        entity.setMobilePhone(request.getMobilePhone());
    }
}
