package com.theron.wallet.mapper;

import com.theron.wallet.dto.response.BeneficiaryResponse;
import com.theron.wallet.entity.Beneficiary;

public final class BeneficiaryMapper {

    private BeneficiaryMapper() {
    }

    public static BeneficiaryResponse toResponse(Beneficiary beneficiary) {
        return BeneficiaryResponse.builder()
                .id(beneficiary.getId())
                .organizationId(beneficiary.getOrganization().getId())
                .createdByUserId(beneficiary.getCreatedBy().getId())
                .name(beneficiary.getName())
                .document(beneficiary.getDocument())
                .pixKey(beneficiary.getPixKey())
                .pixKeyType(beneficiary.getPixKeyType())
                .bankCode(beneficiary.getBankCode())
                .branch(beneficiary.getBranch())
                .account(beneficiary.getAccountNumber())
                .accountType(beneficiary.getAccountType())
                .status(beneficiary.getStatus())
                .createdAt(beneficiary.getCreatedAt())
                .updatedAt(beneficiary.getUpdatedAt())
                .build();
    }
}
