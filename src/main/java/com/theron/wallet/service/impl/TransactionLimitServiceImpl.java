package com.theron.wallet.service.impl;

import com.theron.wallet.dto.request.CreateTransactionLimitRequest;
import com.theron.wallet.dto.request.UpdateTransactionLimitRequest;
import com.theron.wallet.dto.response.TransactionLimitResponse;
import com.theron.wallet.entity.Account;
import com.theron.wallet.entity.Organization;
import com.theron.wallet.entity.Role;
import com.theron.wallet.entity.TransactionLimit;
import com.theron.wallet.entity.User;
import com.theron.wallet.enums.LimitPeriod;
import com.theron.wallet.enums.LimitTransactionType;
import com.theron.wallet.enums.MembershipStatus;
import com.theron.wallet.enums.OrganizationStatus;
import com.theron.wallet.enums.TransactionType;
import com.theron.wallet.exception.DuplicateResourceException;
import com.theron.wallet.exception.ForbiddenException;
import com.theron.wallet.exception.InvalidRequestException;
import com.theron.wallet.exception.ResourceNotFoundException;
import com.theron.wallet.mapper.TransactionLimitMapper;
import com.theron.wallet.repository.AccountRepository;
import com.theron.wallet.repository.MembershipRoleRepository;
import com.theron.wallet.repository.OrganizationMembershipRepository;
import com.theron.wallet.repository.OrganizationRepository;
import com.theron.wallet.repository.RoleRepository;
import com.theron.wallet.repository.TransactionLimitRepository;
import com.theron.wallet.repository.TransactionRepository;
import com.theron.wallet.repository.UserRepository;
import com.theron.wallet.security.PermissionCodes;
import com.theron.wallet.service.AuthorizationService;
import com.theron.wallet.service.LimitContext;
import com.theron.wallet.service.TransactionLimitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionLimitServiceImpl implements TransactionLimitService {

    private final TransactionLimitRepository transactionLimitRepository;
    private final TransactionRepository transactionRepository;
    private final OrganizationRepository organizationRepository;
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final MembershipRoleRepository membershipRoleRepository;
    private final AuthorizationService authorizationService;

    @Override
    @Transactional
    public TransactionLimitResponse create(UUID actorUserId, CreateTransactionLimitRequest request) {
        Organization organization = requireActiveOrganization(request.getOrganizationId());
        authorizationService.requirePermission(
                organization.getId(), actorUserId, PermissionCodes.LIMITS_MANAGE);
        assertSingleScope(request);

        Account account = resolveAccount(request.getAccountId(), organization.getId());
        User user = resolveUser(request.getUserId(), organization.getId());
        Role role = resolveRole(request.getRoleId());

        TransactionLimit limit = TransactionLimit.builder()
                .organization(organization)
                .account(account)
                .user(user)
                .role(role)
                .transactionType(request.getTransactionType())
                .period(request.getPeriod())
                .maxAmount(request.getMaxAmount())
                .enabled(true)
                .build();
        try {
            limit = transactionLimitRepository.saveAndFlush(limit);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateResourceException(
                    "TransactionLimit already exists for this scope, type and period");
        }
        log.info("Transaction limit created: id={}, org={}, type={}, period={}",
                limit.getId(), organization.getId(), limit.getTransactionType(), limit.getPeriod());
        return TransactionLimitMapper.toResponse(limit);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TransactionLimitResponse> listByOrganization(UUID actorUserId, UUID organizationId) {
        requireActiveOrganization(organizationId);
        authorizationService.requirePermission(organizationId, actorUserId, PermissionCodes.LIMITS_READ);
        return transactionLimitRepository.findByOrganizationIdOrderByCreatedAtDesc(organizationId).stream()
                .map(TransactionLimitMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public TransactionLimitResponse getById(UUID actorUserId, UUID id) {
        TransactionLimit limit = transactionLimitRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("TransactionLimit", "id", id));
        authorizationService.requirePermission(
                limit.getOrganization().getId(), actorUserId, PermissionCodes.LIMITS_READ);
        return TransactionLimitMapper.toResponse(limit);
    }

    @Override
    @Transactional
    public TransactionLimitResponse update(UUID actorUserId, UUID id, UpdateTransactionLimitRequest request) {
        TransactionLimit limit = transactionLimitRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("TransactionLimit", "id", id));
        authorizationService.requirePermission(
                limit.getOrganization().getId(), actorUserId, PermissionCodes.LIMITS_MANAGE);
        if (request.getMaxAmount() == null && request.getEnabled() == null) {
            throw new InvalidRequestException("Provide maxAmount and/or enabled");
        }
        if (request.getMaxAmount() != null) {
            limit.setMaxAmount(request.getMaxAmount());
        }
        if (request.getEnabled() != null) {
            limit.setEnabled(request.getEnabled());
        }
        limit = transactionLimitRepository.save(limit);
        return TransactionLimitMapper.toResponse(limit);
    }

    @Override
    @Transactional
    public void assertWithinLimits(LimitContext context) {
        if (context.organizationId() == null || context.actorUserId() == null) {
            return;
        }
        List<TransactionLimit> enabled = transactionLimitRepository.findEnabledByOrganizationAndType(
                context.organizationId(), context.transactionType());
        if (enabled.isEmpty()) {
            return;
        }

        Set<UUID> actorRoleIds = Set.copyOf(membershipRoleRepository.findRoleIdsByOrganizationAndUser(
                context.organizationId(), context.actorUserId()));

        Map<LimitPeriod, TransactionLimit> selected = new EnumMap<>(LimitPeriod.class);
        for (LimitPeriod period : LimitPeriod.values()) {
            pickMostSpecific(enabled, period, context, actorRoleIds)
                    .ifPresent(rule -> selected.put(period, rule));
        }
        if (selected.isEmpty()) {
            return;
        }

        List<UUID> ids = selected.values().stream().map(TransactionLimit::getId).toList();
        Map<UUID, TransactionLimit> locked = transactionLimitRepository.findAllByIdForUpdate(ids).stream()
                .collect(Collectors.toMap(TransactionLimit::getId, l -> l));

        for (Map.Entry<LimitPeriod, TransactionLimit> entry : selected.entrySet()) {
            TransactionLimit lockedRule = locked.get(entry.getValue().getId());
            if (lockedRule == null || !lockedRule.isEnabled()) {
                continue;
            }
            evaluate(lockedRule, context);
        }
    }

    private Optional<TransactionLimit> pickMostSpecific(
            List<TransactionLimit> enabled,
            LimitPeriod period,
            LimitContext context,
            Set<UUID> actorRoleIds) {
        List<TransactionLimit> ofPeriod = enabled.stream()
                .filter(l -> l.getPeriod() == period)
                .toList();
        if (ofPeriod.isEmpty()) {
            return Optional.empty();
        }

        List<TransactionLimit> userRules = ofPeriod.stream()
                .filter(l -> l.getUser() != null && l.getUser().getId().equals(context.actorUserId()))
                .toList();
        if (!userRules.isEmpty()) {
            return Optional.of(mostRestrictive(userRules));
        }

        List<TransactionLimit> roleRules = ofPeriod.stream()
                .filter(l -> l.getRole() != null && actorRoleIds.contains(l.getRole().getId()))
                .toList();
        if (!roleRules.isEmpty()) {
            return Optional.of(mostRestrictive(roleRules));
        }

        if (context.accountId() != null) {
            List<TransactionLimit> accountRules = ofPeriod.stream()
                    .filter(l -> l.getAccount() != null && l.getAccount().getId().equals(context.accountId()))
                    .toList();
            if (!accountRules.isEmpty()) {
                return Optional.of(mostRestrictive(accountRules));
            }
        }

        List<TransactionLimit> orgRules = ofPeriod.stream()
                .filter(l -> l.getAccount() == null && l.getUser() == null && l.getRole() == null)
                .toList();
        if (!orgRules.isEmpty()) {
            return Optional.of(mostRestrictive(orgRules));
        }
        return Optional.empty();
    }

    private static TransactionLimit mostRestrictive(List<TransactionLimit> rules) {
        return rules.stream()
                .min((a, b) -> a.getMaxAmount().compareTo(b.getMaxAmount()))
                .orElseThrow();
    }

    private void evaluate(TransactionLimit rule, LimitContext context) {
        BigDecimal amount = context.amount();
        if (rule.getPeriod() == LimitPeriod.PER_TRANSACTION) {
            if (amount.compareTo(rule.getMaxAmount()) > 0) {
                throw new InvalidRequestException(String.format(
                        "Amount exceeds per-transaction limit. Limit: %s, Requested: %s",
                        rule.getMaxAmount(), amount));
            }
            return;
        }

        LocalDate today = LocalDate.now();
        LocalDateTime start;
        LocalDateTime end;
        if (rule.getPeriod() == LimitPeriod.DAILY) {
            start = today.atStartOfDay();
            end = today.plusDays(1).atStartOfDay();
        } else {
            start = today.withDayOfMonth(1).atStartOfDay();
            end = today.withDayOfMonth(1).plusMonths(1).atStartOfDay();
        }

        TransactionType counted = context.transactionType().toCountedType();
        BigDecimal spent = spentForScope(rule, context, counted, start, end);
        BigDecimal projected = spent.add(amount);
        if (projected.compareTo(rule.getMaxAmount()) > 0) {
            throw new InvalidRequestException(String.format(
                    "Amount exceeds %s limit. Limit: %s, Already used: %s, Requested: %s",
                    rule.getPeriod().name().toLowerCase(), rule.getMaxAmount(), spent, amount));
        }
    }

    private BigDecimal spentForScope(
            TransactionLimit rule,
            LimitContext context,
            TransactionType type,
            LocalDateTime start,
            LocalDateTime end) {
        UUID excludeId = context.excludeTransactionId();
        BigDecimal spent;
        if (rule.getUser() != null) {
            spent = transactionRepository.sumByCreatedByAndTypeInPeriod(
                    rule.getUser().getId(), type, start, end, excludeId);
        } else if (rule.getRole() != null) {
            spent = transactionRepository.sumByCreatedByAndTypeInPeriod(
                    context.actorUserId(), type, start, end, excludeId);
        } else if (rule.getAccount() != null) {
            spent = transactionRepository.sumByAccountAndTypeInPeriod(
                    rule.getAccount().getId(), type, start, end, excludeId);
        } else {
            spent = transactionRepository.sumByOrganizationAndTypeInPeriod(
                    rule.getOrganization().getId(), type, start, end, excludeId);
        }
        return spent != null ? spent : BigDecimal.ZERO;
    }

    private Organization requireActiveOrganization(UUID organizationId) {
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization", "id", organizationId));
        if (organization.getStatus() != OrganizationStatus.ACTIVE) {
            throw new InvalidRequestException("Organization is not ACTIVE");
        }
        return organization;
    }

    private static void assertSingleScope(CreateTransactionLimitRequest request) {
        int scopes = 0;
        if (request.getAccountId() != null) {
            scopes++;
        }
        if (request.getUserId() != null) {
            scopes++;
        }
        if (request.getRoleId() != null) {
            scopes++;
        }
        if (scopes > 1) {
            throw new InvalidRequestException("Provide at most one of accountId, userId, roleId");
        }
    }

    private Account resolveAccount(UUID accountId, UUID organizationId) {
        if (accountId == null) {
            return null;
        }
        Account account = accountRepository.findByIdWithOrganization(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", "id", accountId));
        if (!account.getOrganization().getId().equals(organizationId)) {
            throw new ForbiddenException("Account does not belong to the organization");
        }
        return account;
    }

    private User resolveUser(UUID userId, UUID organizationId) {
        if (userId == null) {
            return null;
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        boolean member = membershipRepository.existsByOrganizationIdAndUserIdAndStatusIn(
                organizationId, userId, List.of(MembershipStatus.ACTIVE));
        if (!member) {
            throw new ForbiddenException("User is not an ACTIVE member of the organization");
        }
        return user;
    }

    private Role resolveRole(UUID roleId) {
        if (roleId == null) {
            return null;
        }
        return roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Role", "id", roleId));
    }
}
