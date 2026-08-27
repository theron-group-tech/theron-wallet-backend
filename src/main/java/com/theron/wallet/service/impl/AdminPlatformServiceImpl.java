package com.theron.wallet.service.impl;

import com.theron.wallet.dto.request.AddOrganizationMemberRequest;
import com.theron.wallet.dto.request.AssignOrganizationAdminRequest;
import com.theron.wallet.dto.request.CreateAccountRequest;
import com.theron.wallet.dto.request.CreateAdminOwnerRequest;
import com.theron.wallet.dto.request.CreateOrganizationRequest;
import com.theron.wallet.dto.request.CreateUserRequest;
import com.theron.wallet.dto.request.UpdateOrganizationRequest;
import com.theron.wallet.dto.request.UpdateSplitConfigRequest;
import com.theron.wallet.dto.response.AccountResponse;
import com.theron.wallet.dto.response.AdminOrganizationDetailResponse;
import com.theron.wallet.dto.response.AdminOwnerResponse;
import com.theron.wallet.dto.response.AdminTransactionResponse;
import com.theron.wallet.dto.response.AsaasBindResponse;
import com.theron.wallet.dto.response.BalanceDivergenceResponse;
import com.theron.wallet.dto.response.OrganizationMembershipResponse;
import com.theron.wallet.dto.response.OrganizationResponse;
import com.theron.wallet.dto.response.PlatformAccountResponse;
import com.theron.wallet.dto.response.SplitConfigResponse;
import com.theron.wallet.dto.response.UserResponse;
import com.theron.wallet.entity.Account;
import com.theron.wallet.entity.Organization;
import com.theron.wallet.entity.Subaccount;
import com.theron.wallet.entity.Transaction;
import com.theron.wallet.entity.Wallet;
import com.theron.wallet.enums.AccountType;
import com.theron.wallet.enums.AsaasBindStatus;
import com.theron.wallet.enums.AuditAction;
import com.theron.wallet.enums.DocumentType;
import com.theron.wallet.enums.MembershipStatus;
import com.theron.wallet.enums.OrganizationStatus;
import com.theron.wallet.enums.RoleCode;
import com.theron.wallet.enums.SubaccountStatus;
import com.theron.wallet.enums.TransactionStatus;
import com.theron.wallet.enums.TransactionType;
import com.theron.wallet.exception.InvalidRequestException;
import com.theron.wallet.exception.ResourceNotFoundException;
import com.theron.wallet.mapper.OrganizationMapper;
import com.theron.wallet.repository.AccountRepository;
import com.theron.wallet.repository.OrganizationRepository;
import com.theron.wallet.repository.SubaccountRepository;
import com.theron.wallet.repository.TransactionRepository;
import com.theron.wallet.repository.WalletRepository;
import com.theron.wallet.service.AccountAsaasProvisioningService;
import com.theron.wallet.service.AccountService;
import com.theron.wallet.service.AdminPlatformService;
import com.theron.wallet.service.AsaasBalanceService;
import com.theron.wallet.service.AuditLogService;
import com.theron.wallet.service.OrganizationMembershipService;
import com.theron.wallet.service.OrganizationService;
import com.theron.wallet.service.PlatformAccountService;
import com.theron.wallet.service.PlatformSplitService;
import com.theron.wallet.service.RoleAssignmentService;
import com.theron.wallet.service.UserService;
import com.theron.wallet.util.TransactionCounterpartHints;
import com.theron.wallet.util.AsaasDocumentRules;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminPlatformServiceImpl implements AdminPlatformService {

    private final OrganizationService organizationService;
    private final OrganizationRepository organizationRepository;
    private final OrganizationMembershipService membershipService;
    private final RoleAssignmentService roleAssignmentService;
    private final UserService userService;
    private final AccountService accountService;
    private final AccountRepository accountRepository;
    private final SubaccountRepository subaccountRepository;
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final AccountAsaasProvisioningService provisioningService;
    private final PlatformSplitService platformSplitService;
    private final AuditLogService auditLogService;
    private final AsaasBalanceService asaasBalanceService;
    private final PlatformAccountService platformAccountService;

    private static final BigDecimal DIVERGENCE_TOLERANCE = new BigDecimal("0.01");

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
    @Transactional
    public AdminOwnerResponse createOrganizationOwner(
            UUID organizationId, CreateAdminOwnerRequest request, UUID adminId) {
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization", "id", organizationId));
        if (organization.getStatus() != OrganizationStatus.ACTIVE) {
            throw new InvalidRequestException("Organization must be ACTIVE to create an OWNER");
        }

        DocumentType documentType = request.getDocumentType() == null ? DocumentType.CNPJ : request.getDocumentType();
        if (documentType != DocumentType.CNPJ) {
            throw new InvalidRequestException(AsaasDocumentRules.CNPJ_REQUIRED_MESSAGE);
        }
        try {
            AsaasDocumentRules.requireCnpj(request.getDocument(), "document");
        } catch (IllegalArgumentException ex) {
            throw new InvalidRequestException(ex.getMessage());
        }
        String ownerCnpj = AsaasDocumentRules.normalize(request.getDocument());

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
                organizationId, user.getId(), List.of(RoleCode.OWNER.name()));

        AccountResponse account = accountService.create(
                organizationId,
                CreateAccountRequest.builder()
                        .name(request.getName())
                        .type(AccountType.MAIN)
                        .build(),
                user.getId(),
                ownerCnpj);

        AsaasBindResponse bind = provisioningService.currentBind(account.getId());
        auditLogService.recordAdmin(
                AuditAction.ORGANIZATION_ADMIN_ASSIGNED,
                organizationId,
                adminId,
                "User",
                user.getId(),
                Map.of(
                        "action", "OWNER_CREATED",
                        "userId", user.getId().toString(),
                        "accountId", account.getId().toString()));

        return AdminOwnerResponse.builder()
                .userId(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .organizationId(organizationId)
                .membershipStatus(MembershipStatus.ACTIVE)
                .accountId(account.getId())
                .asaasBind(bind)
                .build();
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
    public Page<AdminTransactionResponse> listTransactions(
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
        return transactionRepository.findAll(spec, pageable).map(this::toAdminTransaction);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminTransactionResponse getTransaction(UUID transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", "id", transactionId));
        return toAdminTransaction(transaction);
    }

    @Override
    public SplitConfigResponse getSplit() {
        return platformSplitService.getConfig();
    }

    @Override
    public SplitConfigResponse updateSplit(UpdateSplitConfigRequest request, UUID adminId) {
        return platformSplitService.update(request, adminId);
    }

    private AdminTransactionResponse toAdminTransaction(Transaction transaction) {
        Organization organization = transaction.getOrganization();
        Account account = transaction.getAccount();
        String ownerName = null;
        if (account != null && account.getOwnerUser() != null) {
            ownerName = account.getOwnerUser().getName();
        }
        return AdminTransactionResponse.builder()
                .id(transaction.getId())
                .organizationId(organization == null ? null : organization.getId())
                .organizationName(organization == null ? null : organization.getLegalName())
                .accountId(account == null ? null : account.getId())
                .accountName(account == null ? null : account.getName())
                .ownerName(ownerName)
                .walletId(transaction.getWallet() == null ? null : transaction.getWallet().getId())
                .type(transaction.getType())
                .status(transaction.getStatus())
                .amount(transaction.getAmount())
                .currency(transaction.getCurrency())
                .reference(transaction.getReference())
                .description(transaction.getDescription())
                .counterpartHint(TransactionCounterpartHints.resolve(transaction))
                .asaasPaymentId(transaction.getAsaasPaymentId())
                .createdAt(transaction.getCreatedAt())
                .updatedAt(transaction.getUpdatedAt())
                .completedAt(transaction.getCompletedAt())
                .build();
    }

    private AdminOrganizationDetailResponse.AdminAccountSummary toAccountSummary(Account account) {
        Subaccount subaccount = subaccountRepository.findByAccount_Id(account.getId()).orElse(null);
        Wallet wallet = walletRepository.findByAccount_Id(account.getId()).orElse(null);
        Optional<BigDecimal> asaas = asaasBalanceService.fetchAsaasBalance(account.getId());
        boolean asaasUnavailable = subaccount != null
                && subaccount.getStatus() == SubaccountStatus.ACTIVE
                && subaccount.getEncryptedApiKey() != null
                && asaas.isEmpty();
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
                .asaasBalance(asaas.orElse(null))
                .asaasBalanceUnavailable(asaasUnavailable)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public BalanceDivergenceResponse listBalanceDivergences() {
        BigDecimal masterBalance = null;
        try {
            PlatformAccountResponse platform = platformAccountService.get();
            masterBalance = platform.getBalance();
        } catch (Exception ignored) {
            // master balance optional for the panel
        }

        List<BalanceDivergenceResponse.Item> items = new ArrayList<>();
        for (Subaccount subaccount : subaccountRepository.findByStatusWithAccountAndOrganization(
                SubaccountStatus.ACTIVE)) {
            if (subaccount.getEncryptedApiKey() == null || subaccount.getAccount() == null) {
                continue;
            }
            Account account = subaccount.getAccount();
            Optional<BigDecimal> asaasOpt = asaasBalanceService.fetchAsaasBalance(account.getId());
            BigDecimal ledger = asaasBalanceService.ledgerBalance(account.getId());
            boolean unavailable = asaasOpt.isEmpty();
            BigDecimal asaas = asaasOpt.orElse(null);
            BigDecimal difference = unavailable
                    ? null
                    : asaas.subtract(ledger).setScale(2, RoundingMode.HALF_UP);
            boolean diverged = unavailable
                    || difference.abs().compareTo(DIVERGENCE_TOLERANCE) >= 0;
            if (!diverged) {
                continue;
            }
            items.add(BalanceDivergenceResponse.Item.builder()
                    .accountId(account.getId())
                    .organizationId(account.getOrganization() != null
                            ? account.getOrganization().getId() : null)
                    .organizationName(account.getOrganization() != null
                            ? (account.getOrganization().getTradeName() != null
                                    && !account.getOrganization().getTradeName().isBlank()
                                    ? account.getOrganization().getTradeName()
                                    : account.getOrganization().getLegalName())
                            : null)
                    .accountName(account.getName())
                    .asaasBalance(asaas)
                    .ledgerBalance(ledger)
                    .difference(difference)
                    .diverged(true)
                    .asaasBalanceUnavailable(unavailable)
                    .build());
        }

        return BalanceDivergenceResponse.builder()
                .masterAsaasBalance(masterBalance)
                .divergedCount(items.size())
                .items(items)
                .build();
    }

    private AsaasBindStatus toPublicStatus(Subaccount subaccount) {
        if (subaccount.getEncryptedApiKey() != null
                && subaccount.getAsaasAccountId() != null
                && subaccount.getStatus() == SubaccountStatus.ACTIVE) {
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
