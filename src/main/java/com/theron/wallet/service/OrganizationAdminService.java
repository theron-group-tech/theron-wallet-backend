package com.theron.wallet.service;

import com.theron.wallet.dto.request.CreateAccountRequest;
import com.theron.wallet.dto.request.CreateOrganizationEmployeeRequest;
import com.theron.wallet.dto.response.AccountResponse;
import com.theron.wallet.dto.response.AsaasBindResponse;
import com.theron.wallet.dto.response.OrganizationEmployeeResponse;
import com.theron.wallet.dto.response.OrganizationMembershipResponse;
import com.theron.wallet.dto.response.OrganizationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface OrganizationAdminService {

    OrganizationResponse getMyOrganization(UUID actorUserId);

    Page<OrganizationMembershipResponse> listMembers(UUID actorUserId, Pageable pageable);

    OrganizationEmployeeResponse createEmployee(UUID actorUserId, CreateOrganizationEmployeeRequest request);

    List<AccountResponse> listAccounts(UUID actorUserId);

    AccountResponse getAccount(UUID actorUserId, UUID accountId);

    AccountResponse createOwnAccount(UUID actorUserId, CreateAccountRequest request);

    AsaasBindResponse repairAsaasBind(UUID actorUserId, UUID accountId);
}
