package com.theron.wallet.service;

import com.theron.wallet.dto.request.CreateOrganizationRequest;
import com.theron.wallet.dto.request.UpdateOrganizationRequest;
import com.theron.wallet.dto.request.UpdateOrganizationStatusRequest;
import com.theron.wallet.dto.response.OrganizationResponse;
import com.theron.wallet.dto.response.OrganizationStatusResponse;
import com.theron.wallet.enums.OrganizationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface OrganizationService {

    OrganizationResponse create(CreateOrganizationRequest request);

    OrganizationResponse findById(UUID id);

    OrganizationResponse update(UUID id, UpdateOrganizationRequest request);

    OrganizationStatusResponse updateStatus(UUID id, UpdateOrganizationStatusRequest request);

    OrganizationStatusResponse getStatus(UUID id);

    Page<OrganizationResponse> findAll(OrganizationStatus status, Pageable pageable);
}
