package com.theron.wallet.service.impl;

import com.theron.wallet.config.AsaasProperties;
import com.theron.wallet.dto.asaas.AsaasFinanceBalanceResponse;
import com.theron.wallet.dto.response.PlatformAccountResponse;
import com.theron.wallet.entity.PlatformAccount;
import com.theron.wallet.integration.AsaasFinanceClient;
import com.theron.wallet.repository.PlatformAccountRepository;
import com.theron.wallet.service.PlatformAccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformAccountServiceImpl implements PlatformAccountService {

    private final PlatformAccountRepository repository;
    private final AsaasProperties asaasProperties;
    private final AsaasFinanceClient asaasFinanceClient;

    @Override
    @Transactional
    public PlatformAccountResponse get() {
        return toResponse(loadAndSync(), fetchMasterBalance());
    }

    @Override
    @Transactional
    public PlatformAccountResponse syncMasterWalletId(String masterWalletId) {
        PlatformAccount account = loadAndSync();
        if (masterWalletId != null && !masterWalletId.isBlank()) {
            account.setAsaasMasterWalletId(masterWalletId.trim());
            account = repository.save(account);
        }
        return toResponse(account, fetchMasterBalance());
    }

    private PlatformAccount loadAndSync() {
        PlatformAccount account = repository.findById((short) 1).orElseGet(() -> repository.save(
                PlatformAccount.builder()
                        .id((short) 1)
                        .label("Theron Platform")
                        .build()));
        String master = asaasProperties.getMasterWalletId();
        if (master != null && !master.isBlank()
                && (account.getAsaasMasterWalletId() == null || account.getAsaasMasterWalletId().isBlank())) {
            account.setAsaasMasterWalletId(master.trim());
            account = repository.save(account);
        }
        return account;
    }

    private BigDecimal fetchMasterBalance() {
        try {
            AsaasFinanceBalanceResponse balance = asaasFinanceClient.getMasterBalance();
            return balance == null ? null : balance.getBalance();
        } catch (Exception ex) {
            log.warn("Unable to fetch Asaas master balance: {}", ex.getMessage());
            return null;
        }
    }

    private PlatformAccountResponse toResponse(PlatformAccount account, BigDecimal balance) {
        return PlatformAccountResponse.builder()
                .label(account.getLabel())
                .asaasMasterWalletId(account.getAsaasMasterWalletId())
                .balance(balance)
                .currency("BRL")
                .updatedAt(account.getUpdatedAt())
                .build();
    }
}
