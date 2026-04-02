package com.theron.wallet.mapper;

import com.theron.wallet.dto.asaas.AsaasSubaccountRequest;
import com.theron.wallet.dto.request.CreateSubaccountRequest;
import com.theron.wallet.dto.response.SubaccountResponse;
import com.theron.wallet.entity.Customer;
import com.theron.wallet.entity.Subaccount;

public final class SubaccountMapper {

    private SubaccountMapper() {
    }

    public static Subaccount toEntity(CreateSubaccountRequest request, Customer customer) {
        return Subaccount.builder()
                .customer(customer)
                .incomeValue(request.getIncomeValue())
                .address(request.getAddress())
                .addressNumber(request.getAddressNumber())
                .complement(request.getComplement())
                .province(request.getProvince())
                .postalCode(request.getPostalCode())
                .build();
    }

    public static AsaasSubaccountRequest toAsaasRequest(Customer customer, Subaccount subaccount) {
        return AsaasSubaccountRequest.builder()
                .name(customer.getName())
                .email(customer.getEmail())
                .cpfCnpj(customer.getCpfCnpj())
                .mobilePhone(customer.getMobilePhone())
                .phone(customer.getPhone())
                .incomeValue(subaccount.getIncomeValue())
                .address(subaccount.getAddress())
                .addressNumber(subaccount.getAddressNumber())
                .complement(subaccount.getComplement())
                .province(subaccount.getProvince())
                .postalCode(subaccount.getPostalCode())
                .build();
    }

    public static SubaccountResponse toResponse(Subaccount entity) {
        return SubaccountResponse.builder()
                .id(entity.getId())
                .customerId(entity.getCustomer().getId())
                .asaasAccountId(entity.getAsaasAccountId())
                .status(entity.getStatus())
                .statusDescription(entity.getStatus().getDescription())
                .incomeValue(entity.getIncomeValue())
                .address(entity.getAddress())
                .addressNumber(entity.getAddressNumber())
                .complement(entity.getComplement())
                .province(entity.getProvince())
                .postalCode(entity.getPostalCode())
                .statusReason(entity.getStatusReason())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
