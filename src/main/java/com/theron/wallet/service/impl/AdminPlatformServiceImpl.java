package com.theron.wallet.service.impl;

import com.theron.wallet.dto.request.AddOrganizationMemberRequest;
import com.theron.wallet.dto.request.AssignOrganizationAdminRequest;
import com.theron.wallet.dto.request.CreateOrganizationRequest;
import com.theron.wallet.dto.request.UpdateOrganizationRequest;
import com.theron.wallet.dto.request.UpdateSplitConfigRequest;
import com.theron.wallet.dto.response.AdminOrganizationDetailResponse;
import com.theron.wallet.dto.response.AsaasBindResponse;
import com.theron.wallet.dto.response.OrganizationMembershipResponse;
import com.theron.wallet.dto.response.OrganizationResponse;
import com.theron.wallet.dto.response.SplitConfigResponse;
import com.theron.wallet.dto.response.TransactionResponse;
import com.theron.wallet.entity.Account;
import com.theron.wallet.entity.Organization;
import com.theron.wallet.entity.Subaccount;
import com.theron.wallet.entity.Transaction;
import com.theron.wallet.entity.Wallet;
import com.theron.wallet.enums.AsaasBindStatus;
import com.theron.wallet.enums.AuditAction;
import com.theron.wallet.enums.MembershipStatus;
import com.theron.wallet.enums.OrganizationStatus;
import com.theron.wallet.enums.RoleCode;
import com.theron.wallet.enums.SubaccountStatus;
import com.theron.wallet.enums.TransactionStatus;
import com.theron.wallet.enums.TransactionType;
import com.theron.wallet.exception.ResourceNotFoundException;
import com.theron.wallet.mapper.OrganizationMapper;
import com.theron.wallet.mapper.TransactionMapper;
import com.theron.wallet.repository.AccountRepository;
import com.theron.wallet.repository.OrganizationRepository;
import com.theron.wallet.repository.SubaccountRepository;
import com.theron.wallet.repository.TransactionRepository;
import com.theron.wallet.repository.WalletRepository;
import com.theron.wallet.service.AccountAsaasProvisioningService;
import com.theron.wallet.service.AdminPlatformService;
import com.theron.wallet.service.AuditLogService;
import com.theron.wallet.service.OrganizationMembershipService;
import com.theron.wallet.service.OrganizationService;
import com.theron.wallet.service.PlatformSplitService;
import com.theron.wallet.service.RoleAssignmentService;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminPlatformServiceImpl implements AdminPlatformService {

    private final OrganizationService organizationService;
    private final OrganizationRepository organizationRepository;
    private final OrganizationMembershipService membershipService;
    private final RoleAssignmentService roleAssignmentService;
    private final AccountRepository accountRepository;
    private final SubaccountRepository subaccountRepository;
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final AccountAsaasProvisioningService provisioningService;
    private final PlatformSplitService platformSplitService;
    private final AuditLogService auditLogService;

    @Override
    @Transactional
    public OrganizationResponse createOrganization(CreateOrganizationRequest request, UUID adminId) {
        OrganizationResponse created = organizationService.create(request);
        auditLogService.recordAdmin(
                AuditAction.ORGANIZATION_ADMIN_ASSIGNED,
                created.getId(),
                adminId,
                "Organization",
                created.getId(),
                Map.of("action", "CREATED"));
        return created;
    }

    @Override
    @Transactional
    public OrganizationResponse updateOrganization(
            UUID organizationId, UpdateOrganizationRequest request, UUID adminId) {
        OrganizationResponse updated = organizationService.update(organizationId, request);
        auditLogService.recordAdmin(
                AuditAction.ORGANIZATION_ADMIN_ASSIGNED,
                organizationId,
                adminId,
                "Organization",
                organizationId,
                Map.of("action", "UPDATED"));
        return updated;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrganizationResponse> listOrganizations(
            OrganizationStatus status, String document, String q, Pageable pageable) {
        String normalizedDocument = document == null || document.isBlank() ? null : document.replaceAll("\\D", "");
        String query = q == null || q.isBlank() ? null : q.trim().toLowerCase();
        Specification<Organization> spec = (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (normalizedDocument != null) {
                predicates.add(cb.equal(root.get("document"), normalizedDocument));
            }
            if (query != null) {
                String like = "%" + query + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("legalName")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("tradeName"), "")), like),
                        cb.like(root.get("document"), like)
                ));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return organizationRepository.findAll(spec, pageable).map(OrganizationMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminOrganizationDetailResponse getOrganization(UUID organizationId) {
        OrganizationResponse organization = organizationService.findById(organizationId);
        List<AdminOrganizationDetailResponse.AdminAccountSummary> accounts =
                accountRepository.findByOrganization_IdOrderByCreatedAtDesc(organizationId).stream()
                        .map(this::toAccountSummary)
                        .toList();
        return AdminOrganizationDetailResponse.builder()
                .organization(organization)
                .accounts(accounts)
                .build();
    }

    @Override
    @Transactional
    public OrganizationMembershipResponse assignOrganizationAdmin(
            UUID organizationId, AssignOrganizationAdminRequest request, UUID adminId) {
        if (!organizationRepository.existsById(organizationId)) {
            throw new ResourceNotFoundException("Organization", "id", organizationId);
        }
        OrganizationMembershipResponse membership = membershipService.addMember(
                organizationId,
                AddOrganizationMemberRequest.builder()
                        .userId(request.getUserId())
                        .status(MembershipStatus.ACTIVE)
                        .build());
        roleAssignmentService.assignRolesInternal(
                organizationId, request.getUserId(), List.of(RoleCode.OWNER.name()));
        auditLogService.recordAdmin(
                AuditAction.ORGANIZATION_ADMIN_ASSIGNED,
                organizationId,
                adminId,
                "OrganizationMembership",
                request.getUserId(),
                Map.of("userId", request.getUserId().toString()));
        return membership;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrganizationMembershipResponse> listMembers(UUID organizationId, Pageable pageable) {
        return membershipService.listMembers(organizationId, null, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AdminOrganizationDetailResponse.AdminAccountSummary> listAccounts(
            UUID organizationId, AsaasBindStatus asaasStatus, Pageable pageable) {
        List<AdminOrganizationDetailResponse.AdminAccountSummary> all =
                (organizationId == null
                        ? accountRepository.findAll()
                        : accountRepository.findByOrganization_IdOrderByCreatedAtDesc(organizationId))
                        .stream()
                        .map(this::toAccountSummary)
                        .filter(summary -> asaasStatus == null || asaasStatus == summary.getAsaasStatus())
                        .toList();
        return toPage(all, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AsaasBindResponse> listSubaccountBinds(Pageable pageable) {
        return subaccountRepository.findAll(pageable).map(subaccount -> {
            Account account = subaccount.getAccount();
            return AsaasBindResponse.builder()
                    .accountId(account == null ? null : account.getId())
                    .organizationId(account == null ? null : account.getOrganization().getId())
                    .asaasAccountId(subaccount.getAsaasAccountId())
                    .asaasWalletId(subaccount.getAsaasWalletId())
                    .status(toPublicStatus(subaccount))
                    .message(subaccount.getStatus() == SubaccountStatus.FAILED ? subaccount.getStatusReason() : null)
                    .build();
        });
    }

    @Override
    @Transactional
    public AsaasBindResponse provisionSubaccount(UUID accountId, UUID adminId) {
        AsaasBindResponse bind = provisioningService.provisionByAccountId(accountId, null);
        Account account = accountRepository.findByIdWithOrganization(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", "id", accountId));
        auditLogService.recordAdmin(
                AuditAction.ADMIN_SUBACCOUNT_PROVISIONED,
                account.getOrganization().getId(),
                adminId,
                "Account",
                accountId,
                Map.of("status", bind.getStatus().name()));
        return bind;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TransactionResponse> listTransactions(
            UUID organizationId,
            UUID accountId,
            LocalDateTime from,
            LocalDateTime to,
            TransactionType type,
            TransactionStatus status,
            Pageable pageable) {
        Specification<Transaction> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            var orgJoin = root.join("organization", JoinType.LEFT);
            var accountJoin = root.join("account", JoinType.LEFT);
            if (organizationId != null) {
                predicates.add(cb.equal(orgJoin.get("id"), organizationId));
            }
            if (accountId != null) {
                predicates.add(cb.equal(accountJoin.get("id"), accountId));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            }
            if (type != null) {
                predicates.add(cb.equal(root.get("type"), type));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return transactionRepository.findAll(spec, pageable).map(TransactionMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public TransactionResponse getTransaction(UUID transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", "id", transactionId));
        return TransactionMapper.toResponse(transaction);
    }

    @Override
    public SplitConfigResponse getSplit() {
        return platformSplitService.getConfig();
    }

    @Override
    public SplitConfigResponse updateSplit(UpdateSplitConfigRequest request, UUID adminId) {
        return platformSplitService.update(request, adminId);
    }

    private AdminOrganizationDetailResponse.AdminAccountSummary toAccountSummary(Account account) {
        Subaccount subaccount = subaccountRepository.findByAccount_Id(account.getId()).orElse(null);
        Wallet wallet = walletRepository.findByAccount_Id(account.getId()).orElse(null);
        return AdminOrganizationDetailResponse.AdminAccountSummary.builder()
                .accountId(account.getId())
                .ownerUserId(account.getOwnerUser() != null ? account.getOwnerUser().getId() : null)
                .name(account.getName())
                .type(account.getType() == null ? null : account.getType().name())
                .status(account.getStatus() == null ? null : account.getStatus().name())
                .asaasStatus(subaccount == null ? AsaasBindStatus.FAILED : toPublicStatus(subaccount))
                .asaasAccountId(subaccount == null ? null : subaccount.getAsaasAccountId())
                .asaasWalletId(subaccount == null ? null : subaccount.getAsaasWalletId())
                .createdAt(account.getCreatedAt())
                .walletBalance(wallet == null ? null : wallet.getBalance())
                .build();
    }

    private AsaasBindStatus toPublicStatus(Subaccount subaccount) {
        if (subaccount.getEncryptedApiKey() != null
                && subaccount.getAsaasAccountId() != null
                && subaccount.getStatus().allowsOutboundOperations()) {
            return AsaasBindStatus.ACTIVE;
        }
        if (subaccount.getStatus() == SubaccountStatus.FAILED) {
            return AsaasBindStatus.FAILED;
        }
        return AsaasBindStatus.PENDING;
    }

    private static <T> Page<T> toPage(List<T> all, Pageable pageable) {
        int start = (int) pageable.getOffset();
        if (start >= all.size()) {
            return new PageImpl<>(List.of(), pageable, all.size());
        }
        int end = Math.min(start + pageable.getPageSize(), all.size());
        return new PageImpl<>(all.subList(start, end), pageable, all.size());
    }
}
