package com.theron.wallet.service.impl;

import com.theron.wallet.entity.Subaccount;
import com.theron.wallet.entity.Wallet;
import com.theron.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Dashboard ledger sums {@code wallet.balance} by {@code account_id}.
 * Product wallets are created with account only; inbound Asaas webhooks must credit that row.
 */
@Component
@RequiredArgsConstructor
class InboundWalletResolver {

    private final WalletRepository walletRepository;

    Wallet resolveAndLink(Subaccount subaccount) {
        if (subaccount.getAccount() != null) {
            var byAccount = walletRepository.findByAccountIdWithLock(subaccount.getAccount().getId());
            if (byAccount.isPresent()) {
                Wallet wallet = byAccount.get();
                linkSubaccountIfFree(wallet, subaccount);
                return wallet;
            }
        }
        return walletRepository.findBySubaccountIdWithLock(subaccount.getId())
                .orElseThrow(() -> new IllegalStateException("Wallet not found for Asaas subaccount"));
    }

    private void linkSubaccountIfFree(Wallet wallet, Subaccount subaccount) {
        if (wallet.getSubaccount() != null) {
            return;
        }
        boolean alreadyLinked = walletRepository.findBySubaccountId(subaccount.getId())
                .filter(other -> !other.getId().equals(wallet.getId()))
                .isPresent();
        if (alreadyLinked) {
            return;
        }
        wallet.setSubaccount(subaccount);
        walletRepository.save(wallet);
    }
}
