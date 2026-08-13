package com.theron.wallet.mapper;

import com.theron.wallet.dto.request.CreateOrganizationRequest;
import com.theron.wallet.dto.response.OrganizationResponse;
import com.theron.wallet.dto.response.OrganizationStatusResponse;
import com.theron.wallet.entity.Organization;
import com.theron.wallet.enums.OrganizationStatus;

public final class OrganizationMapper {

    private OrganizationMapper() {
    }

    public static Organization toEntity(CreateOrganizationRequest request, String normalizedDocument) {
        return Organization.builder()
                .legalName(request.getLegalName().trim())
                .tradeName(trimToNull(request.getTradeName()))
                .document(normalizedDocument)
                .documentType(request.getDocumentType())
                .status(OrganizationStatus.ACTIVE)
                .build();
    }

    public static OrganizationResponse toResponse(Organization organization) {
        return OrganizationResponse.builder()
                .id(organization.getId())
                .legalName(organization.getLegalName())
                .tradeName(organization.getTradeName())
                .document(organization.getDocument())
                .documentType(organization.getDocumentType())
                .status(organization.getStatus())
                .createdAt(organization.getCreatedAt())
                .updatedAt(organization.getUpdatedAt())
                .build();
    }

    public static OrganizationStatusResponse toStatusResponse(Organization organization) {
        return OrganizationStatusResponse.builder()
                .id(organization.getId())
                .status(organization.getStatus())
                .build();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
