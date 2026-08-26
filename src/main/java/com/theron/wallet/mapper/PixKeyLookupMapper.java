package com.theron.wallet.mapper;

import com.theron.wallet.dto.asaas.AsaasPixExternalKeyResponse;
import com.theron.wallet.dto.response.PixKeyLookupResponse;
import com.theron.wallet.enums.PixKeyType;

import java.util.Locale;

public final class PixKeyLookupMapper {

    private PixKeyLookupMapper() {
    }

    public static PixKeyLookupResponse toResponse(AsaasPixExternalKeyResponse asaas) {
        if (asaas == null) {
            return null;
        }
        String institutionName = null;
        String institutionCode = null;
        if (asaas.getFinancialInstitution() != null) {
            institutionName = asaas.getFinancialInstitution().getName();
            institutionCode = asaas.getFinancialInstitution().getCode();
            if (institutionCode == null && asaas.getFinancialInstitution().getBank() != null) {
                institutionCode = asaas.getFinancialInstitution().getBank().getCode();
            }
            if (institutionName == null && asaas.getFinancialInstitution().getBank() != null) {
                institutionName = asaas.getFinancialInstitution().getBank().getName();
            }
        }
        String ownerName = asaas.getOwner() != null ? asaas.getOwner().getName() : null;
        String ownerDoc = asaas.getOwner() != null ? asaas.getOwner().getCpfCnpj() : null;

        return PixKeyLookupResponse.builder()
                .type(parseType(asaas.getType()))
                .key(asaas.getKey())
                .ispb(asaas.getIspb())
                .ispbName(asaas.getIspbName())
                .institutionName(institutionName)
                .institutionCode(institutionCode)
                .ownerName(ownerName)
                .ownerCpfCnpj(ownerDoc)
                .build();
    }

    private static PixKeyType parseType(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return PixKeyType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
