package com.theron.wallet.mapper;

import com.theron.wallet.dto.asaas.AsaasSubaccountRequest;
import com.theron.wallet.dto.asaas.AsaasWebhookConfigRequest;
import com.theron.wallet.dto.request.CreateSubaccountRequest;
import com.theron.wallet.dto.response.SubaccountResponse;
import com.theron.wallet.entity.Subaccount;

import java.util.List;

public final class SubaccountMapper {

    private SubaccountMapper() {
    }

    /**
     * Maps a CreateSubaccountRequest to a Subaccount entity (PROVISIONING state).
     * Customer link is set separately in the service if customerId is provided.
     */
    public static Subaccount toEntity(CreateSubaccountRequest request) {
        return Subaccount.builder()
                .name(request.getName())
                .email(request.getEmail())
                .loginEmail(request.getLoginEmail())
                .cpfCnpj(request.getCpfCnpj())
                .mobilePhone(request.getMobilePhone())
                .phone(request.getPhone())
                .site(request.getSite())
                .birthDate(request.getBirthDate())
                .companyType(request.getCompanyType() != null ? request.getCompanyType().name() : null)
                .incomeValue(request.getIncomeValue())
                .address(request.getAddress())
                .addressNumber(request.getAddressNumber())
                .complement(request.getComplement())
                .province(request.getProvince())
                .postalCode(request.getPostalCode())
                .build();
    }

    /**
     * Builds the Asaas POST /v3/accounts request body from the persisted Subaccount entity.
     * Webhooks are registered inline in the same request — more atomic than a separate call.
     *
     * @param subaccount the saved PROVISIONING subaccount entity
     * @param webhooks   webhook configs to register inline; pass null or empty to skip
     */
    public static AsaasSubaccountRequest toAsaasRequest(Subaccount subaccount,
                                                        List<AsaasWebhookConfigRequest> webhooks) {
        boolean isPessoaFisica = subaccount.getCpfCnpj() != null && subaccount.getCpfCnpj().length() == 11;

        return AsaasSubaccountRequest.builder()
                .name(subaccount.getName())
                .email(subaccount.getEmail())
                .loginEmail(subaccount.getLoginEmail())
                .cpfCnpj(subaccount.getCpfCnpj())
                .mobilePhone(subaccount.getMobilePhone())
                .phone(subaccount.getPhone())
                .site(subaccount.getSite())
                // birthDate: obrigatório para CPF (PF), ignorado para CNPJ (PJ)
                .birthDate(isPessoaFisica ? subaccount.getBirthDate() : null)
                // companyType: obrigatório para CNPJ (PJ), NUNCA enviado para CPF (PF)
                .companyType(!isPessoaFisica ? subaccount.getCompanyType() : null)
                .incomeValue(subaccount.getIncomeValue())
                .address(subaccount.getAddress())
                .addressNumber(subaccount.getAddressNumber())
                .complement(subaccount.getComplement())
                .province(subaccount.getProvince())
                .postalCode(subaccount.getPostalCode())
                .webhooks(webhooks != null && !webhooks.isEmpty() ? webhooks : null)
                .build();
    }

    /** Convenience overload without inline webhooks. */
    public static AsaasSubaccountRequest toAsaasRequest(Subaccount subaccount) {
        return toAsaasRequest(subaccount, null);
    }

    /**
     * Updates an existing Subaccount entity with new data from a retry request.
     * Used when a FAILED subaccount is being retried with (potentially corrected) data.
     */
    public static void updateFromRequest(Subaccount subaccount, CreateSubaccountRequest request) {
        subaccount.setName(request.getName());
        subaccount.setEmail(request.getEmail());
        subaccount.setLoginEmail(request.getLoginEmail());
        subaccount.setMobilePhone(request.getMobilePhone());
        subaccount.setPhone(request.getPhone());
        subaccount.setSite(request.getSite());
        subaccount.setBirthDate(request.getBirthDate());
        subaccount.setCompanyType(request.getCompanyType() != null ? request.getCompanyType().name() : null);
        subaccount.setIncomeValue(request.getIncomeValue());
        subaccount.setAddress(request.getAddress());
        subaccount.setAddressNumber(request.getAddressNumber());
        subaccount.setComplement(request.getComplement());
        subaccount.setProvince(request.getProvince());
        subaccount.setPostalCode(request.getPostalCode());
    }

    public static SubaccountResponse toResponse(Subaccount entity) {
        String personType = entity.getCpfCnpj() != null && entity.getCpfCnpj().length() == 11
                ? "FISICA"
                : "JURIDICA";

        return SubaccountResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .email(entity.getEmail())
                .loginEmail(entity.getLoginEmail())
                .cpfCnpj(entity.getCpfCnpj())
                .personType(personType)
                .mobilePhone(entity.getMobilePhone())
                .phone(entity.getPhone())
                .birthDate(entity.getBirthDate())
                .companyType(entity.getCompanyType())
                .asaasAccountId(entity.getAsaasAccountId())
                .asaasWalletId(entity.getAsaasWalletId())
                .status(entity.getStatus())
                .statusDescription(entity.getStatus().getDescription())
                .statusReason(entity.getStatusReason())
                .incomeValue(entity.getIncomeValue())
                .address(entity.getAddress())
                .addressNumber(entity.getAddressNumber())
                .complement(entity.getComplement())
                .province(entity.getProvince())
                .postalCode(entity.getPostalCode())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
