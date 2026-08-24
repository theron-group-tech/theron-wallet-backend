package com.theron.wallet.service.impl;

import com.theron.wallet.dto.request.CreateOrganizationRequest;
import com.theron.wallet.dto.request.UpdateOrganizationRequest;
import com.theron.wallet.dto.request.UpdateOrganizationStatusRequest;
import com.theron.wallet.dto.response.OrganizationResponse;
import com.theron.wallet.dto.response.OrganizationStatusResponse;
import com.theron.wallet.entity.Organization;
import com.theron.wallet.enums.DocumentType;
import com.theron.wallet.enums.OrganizationStatus;
import com.theron.wallet.exception.DuplicateResourceException;
import com.theron.wallet.exception.InvalidRequestException;
import com.theron.wallet.exception.ResourceNotFoundException;
import com.theron.wallet.mapper.OrganizationMapper;
import com.theron.wallet.repository.OrganizationRepository;
import com.theron.wallet.service.OrganizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrganizationServiceImpl implements OrganizationService {

    private final OrganizationRepository organizationRepository;

    @Override
    @Transactional
    public OrganizationResponse create(CreateOrganizationRequest request) {
        String document = normalizeDocument(request.getDocument());
        validateDocument(document, request.getDocumentType());

        if (organizationRepository.existsByDocument(document)) {
            throw new DuplicateResourceException("Organization", "document", document);
        }

        Organization organization = OrganizationMapper.toEntity(request, document);
        organization = organizationRepository.save(organization);

        log.info("Organization created: organizationId={}, documentType={}",
                organization.getId(), organization.getDocumentType());

        return OrganizationMapper.toResponse(organization);
    }

    @Override
    @Transactional(readOnly = true)
    public OrganizationResponse findById(UUID id) {
        return OrganizationMapper.toResponse(getOrganizationOrThrow(id));
    }

    @Override
    @Transactional
    public OrganizationResponse update(UUID id, UpdateOrganizationRequest request) {
        Organization organization = getOrganizationOrThrow(id);

        if (request.getLegalName() != null) {
            String legalName = request.getLegalName().trim();
            if (legalName.isEmpty()) {
                throw new InvalidRequestException("legalName must not be blank");
            }
            organization.setLegalName(legalName);
        }
        if (request.getTradeName() != null) {
            String tradeName = request.getTradeName().trim();
            organization.setTradeName(tradeName.isEmpty() ? null : tradeName);
        }
        if (request.getStatus() != null) {
            organization.setStatus(request.getStatus());
        }

        organization = organizationRepository.save(organization);
        log.info("Organization updated: organizationId={}", organization.getId());
        return OrganizationMapper.toResponse(organization);
    }

    @Override
    @Transactional
    public OrganizationStatusResponse updateStatus(UUID id, UpdateOrganizationStatusRequest request) {
        Organization organization = getOrganizationOrThrow(id);
        organization.setStatus(request.getStatus());
        organization = organizationRepository.save(organization);
        log.info("Organization status changed: organizationId={}, status={}",
                organization.getId(), organization.getStatus());
        return OrganizationMapper.toStatusResponse(organization);
    }

    @Override
    @Transactional(readOnly = true)
    public OrganizationStatusResponse getStatus(UUID id) {
        return OrganizationMapper.toStatusResponse(getOrganizationOrThrow(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrganizationResponse> findAll(OrganizationStatus status, Pageable pageable) {
        Page<Organization> page = status != null
                ? organizationRepository.findByStatus(status, pageable)
                : organizationRepository.findAll(pageable);
        return page.map(OrganizationMapper::toResponse);
    }

    private Organization getOrganizationOrThrow(UUID id) {
        return organizationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Organization", "id", id));
    }

    static String normalizeDocument(String document) {
        if (document == null) {
            return null;
        }
        return document.replaceAll("\\D", "");
    }

    static void validateDocument(String document, DocumentType documentType) {
        if (document == null || document.isBlank()) {
            throw new InvalidRequestException("document is required");
        }
        if (documentType == DocumentType.CPF) {
            throw new InvalidRequestException(
                    "Organization document must be CNPJ (Asaas BaaS does not support CPF organizations)");
        }
        if (documentType != DocumentType.CNPJ) {
            throw new InvalidRequestException("documentType must be CNPJ");
        }
        if (document.length() != 14) {
            throw new InvalidRequestException("CNPJ must contain exactly 14 digits");
        }
    }
}
