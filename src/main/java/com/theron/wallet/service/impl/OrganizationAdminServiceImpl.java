package com.theron.wallet.service.impl;

import com.theron.wallet.dto.request.AddOrganizationMemberRequest;
import com.theron.wallet.dto.request.CreateAccountRequest;
import com.theron.wallet.dto.request.CreateOrganizationEmployeeRequest;
import com.theron.wallet.dto.request.CreateUserRequest;
import com.theron.wallet.dto.response.AccountResponse;
import com.theron.wallet.dto.response.AsaasBindResponse;
import com.theron.wallet.dto.response.OrganizationEmployeeResponse;
import com.theron.wallet.dto.response.OrganizationMembershipResponse;
import com.theron.wallet.dto.response.OrganizationResponse;
import com.theron.wallet.dto.response.UserResponse;
import com.theron.wallet.enums.AccountType;
import com.theron.wallet.enums.AuditAction;
import com.theron.wallet.enums.DocumentType;
import com.theron.wallet.enums.MembershipStatus;
import com.theron.wallet.enums.RoleCode;
import com.theron.wallet.exception.AsaasErrorException;
import com.theron.wallet.exception.ForbiddenException;
import com.theron.wallet.repository.AccountRepository;
import com.theron.wallet.security.OrganizationContextResolver;
import com.theron.wallet.security.PermissionCodes;
import com.theron.wallet.security.ResourceAuthorization;
import com.theron.wallet.service.AccountAsaasProvisioningService;
import com.theron.wallet.service.AccountService;
import com.theron.wallet.service.AuditLogService;
import com.theron.wallet.service.OrganizationAdminService;
import com.theron.wallet.service.OrganizationMembershipService;
import com.theron.wallet.service.OrganizationService;
import com.theron.wallet.service.RoleAssignmentService;
import com.theron.wallet.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrganizationAdminServiceImpl implements OrganizationAdminService {

    private final OrganizationContextResolver organizationContextResolver;
    private final OrganizationService organizationService;
    private final OrganizationMembershipService membershipService;
    private final RoleAssignmentService roleAssignmentService;
    private final UserService userService;
    private final AccountService accountService;
    private final AccountRepository accountRepository;
    private final AccountAsaasProvisioningService provisioningService;
    private final ResourceAuthorization resourceAuthorization;
    private final AuditLogService auditLogService;

    @Override
    @Transactional(readOnly = true)
    public OrganizationResponse getMyOrganization(UUID actorUserId) {
        UUID organizationId = organizationContextResolver.requireSingleOrganizationId(actorUserId);
        resourceAuthorization.requireOrganization(actorUserId, organizationId, PermissionCodes.ORGANIZATION_READ);
        return organizationService.findById(organizationId);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrganizationMembershipResponse> listMembers(UUID actorUserId, Pageable pageable) {
        UUID organizationId = organizationContextResolver.requireSingleOrganizationId(actorUserId);
        resourceAuthorization.requireOrganization(actorUserId, organizationId, PermissionCodes.MEMBERS_READ);
        return membershipService.listMembers(organizationId, null, pageable);
    }

    @Override
    @Transactional
    public OrganizationEmployeeResponse createEmployee(UUID actorUserId, CreateOrganizationEmployeeRequest request) {
        UUID organizationId = organizationContextResolver.requireSingleOrganizationId(actorUserId);
        organizationContextResolver.requireOwner(organizationId, actorUserId);
        requireActiveAdminBind(organizationId, actorUserId);

        UserResponse user = userService.create(CreateUserRequest.builder()
                .name(request.getName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .password(request.getPassword())
                .build());

        membershipService.addMember(organizationId, AddOrganizationMemberRequest.builder()
                .userId(user.getId())
                .status(MembershipStatus.ACTIVE)
                .build());
        roleAssignmentService.assignRolesInternal(
                organizationId, user.getId(), List.of(RoleCode.EMPLOYEE.name()));

        DocumentType documentType = request.getDocumentType() == null ? DocumentType.CPF : request.getDocumentType();
        AccountResponse account = accountService.create(
                organizationId,
                CreateAccountRequest.builder()
                        .name(request.getName())
                        .type(AccountType.EMPLOYEE)
                        .build(),
                user.getId(),
                request.getDocument());

        AsaasBindResponse bind = provisioningService.currentBind(account.getId());
        auditLogService.record(
                AuditAction.ORGANIZATION_MEMBER_CREATED,
                organizationId,
                actorUserId,
                "User",
                user.getId(),
                Map.of(
                        "employeeUserId", user.getId().toString(),
                        "accountId", account.getId().toString(),
                        "documentType", documentType.name()));

        return OrganizationEmployeeResponse.builder()
                .userId(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .organizationId(organizationId)
                .membershipStatus(MembershipStatus.ACTIVE)
                .account(account)
                .asaasBind(bind)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountResponse> listAccounts(UUID actorUserId) {
        UUID organizationId = organizationContextResolver.requireSingleOrganizationId(actorUserId);
        resourceAuthorization.requireOrganization(actorUserId, organizationId, PermissionCodes.WALLET_READ);
        List<AccountResponse> accounts = accountService.listByOrganization(organizationId);
        if (organizationContextResolver.isOrgWideViewer(organizationId, actorUserId)) {
            return accounts;
        }
        return accounts.stream()
                .filter(account -> actorUserId.equals(account.getOwnerUserId()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AccountResponse getAccount(UUID actorUserId, UUID accountId) {
        resourceAuthorization.requireAccount(actorUserId, accountId, PermissionCodes.WALLET_READ);
        UUID organizationId = organizationContextResolver.requireSingleOrganizationId(actorUserId);
        AccountResponse account = accountService.findById(accountId);
        if (!organizationId.equals(account.getOrganizationId())) {
            throw new ForbiddenException("Access denied");
        }
        return account;
    }

    @Override
    @Transactional
    public AccountResponse createOwnAccount(UUID actorUserId, CreateAccountRequest request) {
        UUID organizationId = organizationContextResolver.requireSingleOrganizationId(actorUserId);
        organizationContextResolver.requireOwner(organizationId, actorUserId);
        resourceAuthorization.requireOrganization(actorUserId, organizationId, PermissionCodes.ORGANIZATION_UPDATE);
        return accountService.create(organizationId, request, actorUserId);
    }

    @Override
    @Transactional
    public AsaasBindResponse repairAsaasBind(UUID actorUserId, UUID accountId) {
        resourceAuthorization.requireAccount(actorUserId, accountId, PermissionCodes.ORGANIZATION_UPDATE);
        return provisioningService.provisionByAccountId(accountId, null);
    }

    private void requireActiveAdminBind(UUID organizationId, UUID actorUserId) {
        var account = accountRepository.findByOrganization_IdAndOwnerUser_Id(organizationId, actorUserId)
                .orElseThrow(() -> new AsaasErrorException(
                        "Organization admin must provision their own Asaas subaccount before creating employees"));
        if (!provisioningService.hasActiveBind(account.getId())) {
            throw new AsaasErrorException(
                    "Organization admin must provision their own Asaas subaccount before creating employees");
        }
    }
}
