package com.theron.wallet.service.impl;

import com.theron.wallet.config.AsaasProperties;
import com.theron.wallet.dto.response.PlatformAccountResponse;
import com.theron.wallet.entity.PlatformAccount;
import com.theron.wallet.repository.PlatformAccountRepository;
import com.theron.wallet.service.PlatformAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlatformAccountServiceImpl implements PlatformAccountService {

    private final PlatformAccountRepository repository;
    private final AsaasProperties asaasProperties;

    @Override
    @Transactional
    public PlatformAccountResponse get() {
        return toResponse(loadAndSync());
    }

    @Override
    @Transactional
    public PlatformAccountResponse syncMasterWalletId(String masterWalletId) {
        PlatformAccount account = loadAndSync();
        if (masterWalletId != null && !masterWalletId.isBlank()) {
            account.setAsaasMasterWalletId(masterWalletId.trim());
            account = repository.save(account);
        }
        return toResponse(account);
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

    private PlatformAccountResponse toResponse(PlatformAccount account) {
        return PlatformAccountResponse.builder()
                .label(account.getLabel())
                .asaasMasterWalletId(account.getAsaasMasterWalletId())
                .updatedAt(account.getUpdatedAt())
                .build();
    }
}
