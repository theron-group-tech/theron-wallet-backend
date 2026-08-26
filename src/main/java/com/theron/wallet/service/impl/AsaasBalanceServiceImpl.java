package com.theron.wallet.service.impl;

import com.theron.wallet.dto.asaas.AsaasFinanceBalanceResponse;
import com.theron.wallet.entity.Subaccount;
import com.theron.wallet.entity.Wallet;
import com.theron.wallet.enums.SubaccountStatus;
import com.theron.wallet.integration.AsaasFinanceClient;
import com.theron.wallet.repository.SubaccountRepository;
import com.theron.wallet.repository.WalletRepository;
import com.theron.wallet.security.AsaasApiKeyResolver;
import com.theron.wallet.service.AsaasBalanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AsaasBalanceServiceImpl implements AsaasBalanceService {

    private final SubaccountRepository subaccountRepository;
    private final WalletRepository walletRepository;
    private final AsaasApiKeyResolver asaasApiKeyResolver;
    private final AsaasFinanceClient asaasFinanceClient;

    @Override
    @Transactional(readOnly = true)
    public Optional<BigDecimal> fetchAsaasBalance(UUID accountId) {
        if (accountId == null) {
            return Optional.empty();
        }
        Subaccount subaccount = subaccountRepository.findByAccount_Id(accountId).orElse(null);
        if (subaccount == null
                || subaccount.getStatus() != SubaccountStatus.ACTIVE
                || subaccount.getEncryptedApiKey() == null) {
            return Optional.empty();
        }
        try {
            String apiKey = asaasApiKeyResolver.resolveForSubaccount(subaccount.getId());
            AsaasFinanceBalanceResponse response = asaasFinanceClient.getBalance(apiKey);
            if (response == null || response.getBalance() == null) {
                return Optional.empty();
            }
            return Optional.of(money(response.getBalance()));
        } catch (Exception ex) {
            log.warn("Unable to fetch Asaas balance for accountId={}: {}", accountId, ex.getMessage());
            return Optional.empty();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal displayBalance(UUID accountId) {
        return fetchAsaasBalance(accountId).orElseGet(() -> ledgerBalance(accountId));
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal sumDisplayBalances(Collection<UUID> accountIds) {
        if (accountIds == null || accountIds.isEmpty()) {
            return money(BigDecimal.ZERO);
        }
        BigDecimal total = BigDecimal.ZERO;
        for (UUID accountId : accountIds) {
            total = total.add(displayBalance(accountId));
        }
        return money(total);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal ledgerBalance(UUID accountId) {
        return walletRepository.findByAccount_Id(accountId)
                .map(Wallet::getBalance)
                .map(AsaasBalanceServiceImpl::money)
                .orElse(money(BigDecimal.ZERO));
    }

    private static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }
}
