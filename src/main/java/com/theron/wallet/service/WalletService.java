package com.theron.wallet.service;

import com.theron.wallet.dto.response.WalletResponse;

import java.math.BigDecimal;
import java.util.UUID;

public interface WalletService {

    WalletResponse findByCustomerId(UUID customerId);

    WalletResponse findById(UUID walletId);

    WalletResponse getOrCreateWallet(UUID customerId);

    void credit(UUID walletId, BigDecimal amount);

    void debit(UUID walletId, BigDecimal amount);
}
