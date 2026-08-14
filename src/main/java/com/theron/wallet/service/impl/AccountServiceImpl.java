package com.theron.wallet.service.impl;

import com.theron.wallet.dto.request.CreateAccountRequest;
import com.theron.wallet.dto.request.UpdateAccountRequest;
import com.theron.wallet.dto.response.AccountResponse;
import com.theron.wallet.dto.response.WalletResponse;
import com.theron.wallet.entity.Account;
import com.theron.wallet.entity.Organization;
import com.theron.wallet.entity.Wallet;
import com.theron.wallet.enums.AccountStatus;
import com.theron.wallet.enums.OrganizationStatus;
import com.theron.wallet.exception.InvalidRequestException;
import com.theron.wallet.exception.ResourceNotFoundException;
import com.theron.wallet.mapper.AccountMapper;
import com.theron.wallet.mapper.WalletMapper;
import com.theron.wallet.repository.AccountRepository;
import com.theron.wallet.repository.OrganizationRepository;
import com.theron.wallet.repository.WalletRepository;
import com.theron.wallet.service.AccountService;
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
    private final WalletRepository walletRepository;

    @Override
    @Transactional
    public AccountResponse create(UUID organizationId, CreateAccountRequest request) {
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization", "id", organizationId));

        if (organization.getStatus() != OrganizationStatus.ACTIVE) {
            throw new InvalidRequestException("Organization is not ACTIVE");
        }

        String currency = normalizeCurrency(request.getCurrency());
        Account account = Account.builder()
                .organization(organization)
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

        log.info("Account created: accountId={}, organizationId={}", account.getId(), organizationId);
        return AccountMapper.toResponse(account);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountResponse> listByOrganization(UUID organizationId) {
        if (!organizationRepository.existsById(organizationId)) {
            throw new ResourceNotFoundException("Organization", "id", organizationId);
        }
        return accountRepository.findByOrganization_IdOrderByCreatedAtDesc(organizationId).stream()
                .map(AccountMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AccountResponse findById(UUID id) {
        return AccountMapper.toResponse(getAccountOrThrow(id));
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
        return AccountMapper.toResponse(account);
    }

    @Override
    @Transactional(readOnly = true)
    public WalletResponse findWallet(UUID accountId) {
        getAccountOrThrow(accountId);
        Wallet wallet = walletRepository.findByAccount_Id(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet", "accountId", accountId));
        return WalletMapper.toResponse(wallet);
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
