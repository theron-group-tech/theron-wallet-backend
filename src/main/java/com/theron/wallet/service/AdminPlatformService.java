package com.theron.wallet.service;

import com.theron.wallet.dto.request.AssignOrganizationAdminRequest;
import com.theron.wallet.dto.request.CreateAdminOwnerRequest;
import com.theron.wallet.dto.request.CreateOrganizationRequest;
import com.theron.wallet.dto.request.UpdateOrganizationRequest;
import com.theron.wallet.dto.request.UpdateSplitConfigRequest;
import com.theron.wallet.dto.response.AdminOrganizationDetailResponse;
import com.theron.wallet.dto.response.AdminOwnerResponse;
import com.theron.wallet.dto.response.AdminTransactionResponse;
import com.theron.wallet.dto.response.AsaasBindResponse;
import com.theron.wallet.dto.response.OrganizationMembershipResponse;
import com.theron.wallet.dto.response.OrganizationResponse;
import com.theron.wallet.dto.response.SplitConfigResponse;
import com.theron.wallet.enums.AsaasBindStatus;
import com.theron.wallet.enums.OrganizationStatus;
import com.theron.wallet.enums.TransactionStatus;
import com.theron.wallet.enums.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.UUID;

public interface AdminPlatformService {

    OrganizationResponse createOrganization(CreateOrganizationRequest request, UUID adminId);

    OrganizationResponse updateOrganization(UUID organizationId, UpdateOrganizationRequest request, UUID adminId);

    Page<OrganizationResponse> listOrganizations(OrganizationStatus status, String document, String q, Pageable pageable);

    AdminOrganizationDetailResponse getOrganization(UUID organizationId);

    OrganizationMembershipResponse assignOrganizationAdmin(
            UUID organizationId, AssignOrganizationAdminRequest request, UUID adminId);

    AdminOwnerResponse createOrganizationOwner(
            UUID organizationId, CreateAdminOwnerRequest request, UUID adminId);

    Page<OrganizationMembershipResponse> listMembers(UUID organizationId, Pageable pageable);

    Page<AdminOrganizationDetailResponse.AdminAccountSummary> listAccounts(
            UUID organizationId, AsaasBindStatus asaasStatus, Pageable pageable);

    Page<AsaasBindResponse> listSubaccountBinds(Pageable pageable);

    AsaasBindResponse provisionSubaccount(UUID accountId, UUID adminId);

    Page<AdminTransactionResponse> listTransactions(
            UUID organizationId,
            UUID accountId,
            LocalDateTime from,
            LocalDateTime to,
            TransactionType type,
            TransactionStatus status,
            Pageable pageable);

    AdminTransactionResponse getTransaction(UUID transactionId);

    SplitConfigResponse getSplit();

    SplitConfigResponse updateSplit(UpdateSplitConfigRequest request, UUID adminId);
}
