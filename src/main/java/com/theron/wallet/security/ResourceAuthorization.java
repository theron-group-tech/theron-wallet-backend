package com.theron.wallet.security;

import com.theron.wallet.entity.Account;
import com.theron.wallet.entity.Subaccount;
import com.theron.wallet.entity.Transaction;
import com.theron.wallet.entity.Wallet;
import com.theron.wallet.exception.ForbiddenException;
import com.theron.wallet.exception.ResourceNotFoundException;
import com.theron.wallet.repository.AccountRepository;
import com.theron.wallet.repository.OrganizationRepository;
import com.theron.wallet.repository.SubaccountRepository;
import com.theron.wallet.repository.TransactionRepository;
import com.theron.wallet.repository.WalletRepository;
import com.theron.wallet.service.AuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ResourceAuthorization {

    private final AuthorizationService authorizationService;
    private final TenantAccessGuard tenantAccessGuard;
    private final OrganizationContextResolver organizationContextResolver;
    private final OrganizationRepository organizationRepository;
    private final AccountRepository accountRepository;
    private final WalletRepository walletRepository;
    private final SubaccountRepository subaccountRepository;
    private final TransactionRepository transactionRepository;

    @Transactional(readOnly = true)
    public UUID requireOrganization(UUID actorUserId, UUID organizationId, String permission) {
        if (!organizationRepository.existsById(organizationId)) {
            throw new ResourceNotFoundException("Organization", "id", organizationId);
        }
        authorizationService.requirePermission(organizationId, actorUserId, permission);
        return organizationId;
    }

    @Transactional(readOnly = true)
    public UUID requireAccount(UUID actorUserId, UUID accountId, String permission) {
        Account account = accountRepository.findByIdWithOrganization(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", "id", accountId));
        UUID orgId = account.getOrganization().getId();
        authorizationService.requirePermission(orgId, actorUserId, permission);
        requireOwnAccount(actorUserId, account);
        return orgId;
    }

    @Transactional(readOnly = true)
    public UUID requireWallet(UUID actorUserId, UUID walletId, String permission) {
        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet", "id", walletId));
        return requireWalletEntity(actorUserId, wallet, permission);
    }

    @Transactional(readOnly = true)
    public UUID requireSubaccount(UUID actorUserId, UUID subaccountId, String permission) {
        Subaccount subaccount = subaccountRepository.findById(subaccountId)
                .orElseThrow(() -> new ResourceNotFoundException("Subaccount", "id", subaccountId));
        UUID orgId = orgIdOf(subaccount);
        authorizationService.requirePermission(orgId, actorUserId, permission);
        if (subaccount.getAccount() != null) {
            Account account = accountRepository.findByIdWithOrganization(subaccount.getAccount().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Account", "id", subaccount.getAccount().getId()));
            requireOwnAccount(actorUserId, account);
        }
        return orgId;
    }

    @Transactional(readOnly = true)
    public UUID requireTransaction(UUID actorUserId, UUID transactionId, String permission) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", "id", transactionId));
        if (transaction.getAccount() != null) {
            return requireAccount(actorUserId, transaction.getAccount().getId(), permission);
        }
        if (transaction.getWallet() != null) {
            return requireWalletEntity(actorUserId, transaction.getWallet(), permission);
        }
        throw new ForbiddenException("Transaction is not linked to an organization");
    }

    public void requirePathOrganization(UUID pathOrganizationId, UUID resourceOrganizationId) {
        tenantAccessGuard.requireSameOrganization(pathOrganizationId, resourceOrganizationId);
    }

    /** @deprecated Financial resources are always own-account-only. */
    public boolean isOrgWideViewer(UUID actorUserId, UUID organizationId) {
        return organizationContextResolver.isOrgWideViewer(organizationId, actorUserId);
    }

    private void requireOwnAccount(UUID actorUserId, Account account) {
        if (account.getOwnerUser() == null || !account.getOwnerUser().getId().equals(actorUserId)) {
            throw new ForbiddenException("Access denied");
        }
    }

    private UUID requireWalletEntity(UUID actorUserId, Wallet wallet, String permission) {
        UUID orgId = orgIdOf(wallet);
        authorizationService.requirePermission(orgId, actorUserId, permission);
        if (wallet.getAccount() != null) {
            Account account = accountRepository.findByIdWithOrganization(wallet.getAccount().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Account", "id", wallet.getAccount().getId()));
            requireOwnAccount(actorUserId, account);
        }
        return orgId;
    }

    private UUID orgIdOf(Wallet wallet) {
        if (wallet.getAccount() != null) {
            Account account = accountRepository.findByIdWithOrganization(wallet.getAccount().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Account", "id", wallet.getAccount().getId()));
            return account.getOrganization().getId();
        }
        if (wallet.getSubaccount() != null) {
            return orgIdOf(wallet.getSubaccount());
        }
        throw new ForbiddenException("Wallet is not linked to an organization");
    }

    private UUID orgIdOf(Subaccount subaccount) {
        if (subaccount.getAccount() == null) {
            throw new ForbiddenException("Subaccount is not linked to an organization");
        }
        Account account = accountRepository.findByIdWithOrganization(subaccount.getAccount().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Account", "id", subaccount.getAccount().getId()));
        return account.getOrganization().getId();
    }
}
