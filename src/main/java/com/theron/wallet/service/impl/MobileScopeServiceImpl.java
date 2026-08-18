package com.theron.wallet.service.impl;

import com.theron.wallet.entity.Account;
import com.theron.wallet.entity.OrganizationMembership;
import com.theron.wallet.enums.MembershipStatus;
import com.theron.wallet.exception.ForbiddenException;
import com.theron.wallet.exception.ResourceNotFoundException;
import com.theron.wallet.repository.AccountRepository;
import com.theron.wallet.repository.OrganizationMembershipRepository;
import com.theron.wallet.repository.OrganizationRepository;
import com.theron.wallet.service.MobileScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MobileScopeServiceImpl implements MobileScopeService {

    private final OrganizationMembershipRepository membershipRepository;
    private final OrganizationRepository organizationRepository;
    private final AccountRepository accountRepository;

    @Override
    @Transactional(readOnly = true)
    public List<UUID> organizationIds(UUID userId, UUID organizationId) {
        List<OrganizationMembership> memberships =
                membershipRepository.findByUserIdAndStatus(userId, MembershipStatus.ACTIVE);
        List<UUID> allowed = memberships.stream()
                .map(membership -> membership.getOrganization().getId())
                .toList();
        if (organizationId == null) {
            return allowed;
        }
        if (!organizationRepository.existsById(organizationId)) {
            throw new ResourceNotFoundException("Organization", "id", organizationId);
        }
        if (!allowed.contains(organizationId)) {
            throw new ForbiddenException("Cannot access another organization's data");
        }
        return List.of(organizationId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Account> accounts(UUID userId, UUID organizationId, UUID accountId) {
        List<UUID> orgIds = organizationIds(userId, organizationId);
        if (orgIds.isEmpty()) {
            if (accountId != null) {
                if (!accountRepository.existsById(accountId)) {
                    throw new ResourceNotFoundException("Account", "id", accountId);
                }
                throw new ForbiddenException("Cannot access another organization's account");
            }
            return List.of();
        }
        List<Account> accounts = accountRepository.findByOrganization_IdInOrderByCreatedAtDesc(orgIds);
        if (accountId == null) {
            return accounts;
        }
        Account match = accounts.stream()
                .filter(account -> account.getId().equals(accountId))
                .findFirst()
                .orElse(null);
        if (match != null) {
            return List.of(match);
        }
        if (!accountRepository.existsById(accountId)) {
            throw new ResourceNotFoundException("Account", "id", accountId);
        }
        throw new ForbiddenException("Cannot access another organization's account");
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> accountIds(UUID userId, UUID organizationId, UUID accountId) {
        return accounts(userId, organizationId, accountId).stream()
                .map(Account::getId)
                .toList();
    }
}
