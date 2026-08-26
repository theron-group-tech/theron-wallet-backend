package com.theron.wallet.service.impl;

import com.theron.wallet.dto.request.CreateAccountRequest;
import com.theron.wallet.dto.request.UpdateAccountRequest;
import com.theron.wallet.dto.response.AccountResponse;
import com.theron.wallet.dto.response.AsaasBindResponse;
import com.theron.wallet.dto.response.WalletResponse;
import com.theron.wallet.entity.Account;
import com.theron.wallet.entity.Organization;
import com.theron.wallet.entity.User;
import com.theron.wallet.entity.Wallet;
import com.theron.wallet.enums.AccountStatus;
import com.theron.wallet.enums.OrganizationStatus;
import com.theron.wallet.enums.SubaccountStatus;
import com.theron.wallet.exception.DuplicateResourceException;
import com.theron.wallet.exception.InvalidRequestException;
import com.theron.wallet.exception.ResourceNotFoundException;
import com.theron.wallet.mapper.AccountMapper;
import com.theron.wallet.mapper.WalletMapper;
import com.theron.wallet.repository.AccountRepository;
import com.theron.wallet.repository.OrganizationRepository;
import com.theron.wallet.repository.UserRepository;
import com.theron.wallet.repository.WalletRepository;
import com.theron.wallet.service.AccountAsaasProvisioningService;
import com.theron.wallet.service.AccountService;
import com.theron.wallet.service.AsaasBalanceService;
import com.theron.wallet.service.LedgerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountServiceImpl implements AccountService {

    private final AccountRepository accountRepository;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final LedgerService ledgerService;
    private final AccountAsaasProvisioningService accountAsaasProvisioningService;
    private final AsaasBalanceService asaasBalanceService;

    @Override
    @Transactional
    public AccountResponse create(UUID organizationId, CreateAccountRequest request) {
        return create(organizationId, request, null, null);
    }

    @Override
    @Transactional
    public AccountResponse create(UUID organizationId, CreateAccountRequest request, UUID ownerUserId) {
        return create(organizationId, request, ownerUserId, null);
    }

    @Override
    @Transactional
    public AccountResponse create(
            UUID organizationId, CreateAccountRequest request, UUID ownerUserId, String asaasDocument) {
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization", "id", organizationId));

        if (organization.getStatus() != OrganizationStatus.ACTIVE) {
            throw new InvalidRequestException("Organization is not ACTIVE");
        }

        User owner = null;
        if (ownerUserId != null) {
            if (accountRepository.existsByOrganization_IdAndOwnerUser_Id(organizationId, ownerUserId)) {
                throw new DuplicateResourceException("Account already exists for this user in the organization");
            }
            owner = userRepository.findById(ownerUserId)
                    .orElseThrow(() -> new ResourceNotFoundException("User", "id", ownerUserId));
        }

        String currency = normalizeCurrency(request.getCurrency());
        Account account = Account.builder()
                .organization(organization)
                .ownerUser(owner)
                .name(request.getName().trim())
                .type(request.getType())
                .status(AccountStatus.ACTIVE)
                .currency(currency)
                .build();
        account = accountRepository.save(account);

        Wallet wallet = Wallet.builder()
                .account(account)
                .currency(currency)
                .build();
        walletRepository.save(wallet);
        ledgerService.provisionForAccount(account);

        if (owner != null) {
            accountAsaasProvisioningService.provision(account, asaasDocument);
        }

        log.info("Account created: accountId={}, organizationId={}, ownerUserId={}",
                account.getId(), organizationId, ownerUserId);
        return toEnrichedResponse(account);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountResponse> listByOrganization(UUID organizationId) {
        if (!organizationRepository.existsById(organizationId)) {
            throw new ResourceNotFoundException("Organization", "id", organizationId);
        }
        return accountRepository.findByOrganization_IdOrderByCreatedAtDesc(organizationId).stream()
                .map(this::toEnrichedResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AccountResponse findById(UUID id) {
        return toEnrichedResponse(getAccountOrThrow(id));
    }

    @Override
    @Transactional
    public AccountResponse update(UUID id, UpdateAccountRequest request) {
        Account account = getAccountOrThrow(id);

        if (request.getName() != null) {
            String name = request.getName().trim();
            if (name.isEmpty()) {
                throw new InvalidRequestException("name must not be blank");
            }
            account.setName(name);
        }
        if (request.getStatus() != null) {
            account.setStatus(request.getStatus());
        }

        account = accountRepository.save(account);
        log.info("Account updated: accountId={}", account.getId());
        return toEnrichedResponse(account);
    }

    @Override
    @Transactional(readOnly = true)
    public WalletResponse findWallet(UUID accountId) {
        getAccountOrThrow(accountId);
        Wallet wallet = walletRepository.findByAccount_Id(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet", "accountId", accountId));
        var asaas = asaasBalanceService.fetchAsaasBalance(accountId);
        boolean unavailable = asaas.isEmpty()
                && wallet.getSubaccount() != null
                && wallet.getSubaccount().getStatus() == SubaccountStatus.ACTIVE
                && wallet.getSubaccount().getEncryptedApiKey() != null;
        return WalletMapper.toResponse(wallet, asaas.orElse(wallet.getBalance()), wallet.getBalance(), unavailable);
    }

    private AccountResponse toEnrichedResponse(Account account) {
        AsaasBindResponse bind = accountAsaasProvisioningService.currentBind(account.getId());
        return AccountMapper.toResponse(account, bind);
    }

    private Account getAccountOrThrow(UUID id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account", "id", id));
    }

    private static String normalizeCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            return "BRL";
        }
        String normalized = currency.trim().toUpperCase();
        if (normalized.length() != 3) {
            throw new InvalidRequestException("currency must be a 3-letter ISO-4217 code");
        }
        return normalized;
    }
}
