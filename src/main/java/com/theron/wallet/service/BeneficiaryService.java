package com.theron.wallet.service;

import com.theron.wallet.dto.request.CreateBeneficiaryRequest;
import com.theron.wallet.dto.request.UpdateBeneficiaryRequest;
import com.theron.wallet.dto.response.BeneficiaryResponse;

import java.util.List;
import java.util.UUID;

public interface BeneficiaryService {

    BeneficiaryResponse create(UUID actorUserId, CreateBeneficiaryRequest request);

    List<BeneficiaryResponse> listByOrganization(UUID actorUserId, UUID organizationId);

    BeneficiaryResponse findById(UUID actorUserId, UUID id);

    BeneficiaryResponse update(UUID actorUserId, UUID id, UpdateBeneficiaryRequest request);

    void delete(UUID actorUserId, UUID id);
}
