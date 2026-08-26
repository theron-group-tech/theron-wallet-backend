package com.theron.wallet.service.impl;

import com.theron.wallet.dto.response.WalletResponse;
import com.theron.wallet.entity.Subaccount;
import com.theron.wallet.entity.Wallet;
import com.theron.wallet.enums.SubaccountStatus;
import com.theron.wallet.exception.InsufficientBalanceException;
import com.theron.wallet.exception.ResourceNotFoundException;
import com.theron.wallet.mapper.WalletMapper;
import com.theron.wallet.repository.SubaccountRepository;
import com.theron.wallet.repository.WalletRepository;
import com.theron.wallet.service.AsaasBalanceService;
import com.theron.wallet.service.LedgerService;
import com.theron.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletServiceImpl implements WalletService {

    private final WalletRepository walletRepository;
    private final SubaccountRepository subaccountRepository;
    private final LedgerService ledgerService;
    private final AsaasBalanceService asaasBalanceService;

    @Override
    @Transactional(readOnly = true)
    public WalletResponse findBySubaccountId(UUID subaccountId) {
        Wallet wallet = walletRepository.findBySubaccountId(subaccountId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet", "subaccountId", subaccountId));
        return toEnrichedResponse(wallet);
    }

    @Override
    @Transactional(readOnly = true)
    public WalletResponse findById(UUID walletId) {
        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet", "id", walletId));
        return toEnrichedResponse(wallet);
    }

    @Override
    @Transactional
    public WalletResponse getOrCreateWallet(UUID subaccountId) {
        return walletRepository.findBySubaccountId(subaccountId)
                .map(this::toEnrichedResponse)
                .orElseGet(() -> createWallet(subaccountId));
    }

    private WalletResponse toEnrichedResponse(Wallet wallet) {
        UUID accountId = wallet.getAccount() != null ? wallet.getAccount().getId() : null;
        BigDecimal ledger = wallet.getBalance();
        if (accountId == null) {
            return WalletMapper.toResponse(wallet, ledger, ledger, null);
        }
        var asaas = asaasBalanceService.fetchAsaasBalance(accountId);
        boolean unavailable = asaas.isEmpty()
                && wallet.getSubaccount() != null
                && wallet.getSubaccount().getStatus() == SubaccountStatus.ACTIVE
                && wallet.getSubaccount().getEncryptedApiKey() != null;
        return WalletMapper.toResponse(wallet, asaas.orElse(ledger), ledger, unavailable);
    }

    @Override
    @Transactional
    public void credit(UUID walletId, BigDecimal amount) {
        Wallet wallet = walletRepository.findByIdForUpdate(walletId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet", "id", walletId));

        wallet.credit(amount);
        walletRepository.save(wallet);
        postLedgerCreditIfAccountPresent(wallet, amount);

        log.info("Wallet credited: walletId={}, amount={}, newBalance={}", walletId, amount, wallet.getBalance());
    }

    @Override
    @Transactional
    public void debit(UUID walletId, BigDecimal amount) {
        Wallet wallet = walletRepository.findByIdForUpdate(walletId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet", "id", walletId));

        if (wallet.getBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException(
                    String.format("Insufficient balance. Available: %s, Requested: %s", wallet.getBalance(), amount));
        }

        wallet.debit(amount);
        walletRepository.save(wallet);
        postLedgerDebitIfAccountPresent(wallet, amount);

        log.info("Wallet debited: walletId={}, amount={}, newBalance={}", walletId, amount, wallet.getBalance());
    }

    private void postLedgerCreditIfAccountPresent(Wallet wallet, BigDecimal amount) {
        if (wallet.getAccount() == null) {
            return;
        }
        ledgerService.postCredit(
                wallet.getAccount().getId(),
                amount,
                "ledger:wallet-credit:" + wallet.getId() + ":" + UUID.randomUUID(),
                wallet.getId().toString());
    }

    private void postLedgerDebitIfAccountPresent(Wallet wallet, BigDecimal amount) {
        if (wallet.getAccount() == null) {
            return;
        }
        ledgerService.postDebit(
                wallet.getAccount().getId(),
                amount,
                "ledger:wallet-debit:" + wallet.getId() + ":" + UUID.randomUUID(),
                wallet.getId().toString());
    }

    private WalletResponse createWallet(UUID subaccountId) {
        Subaccount subaccount = subaccountRepository.findById(subaccountId)
                .orElseThrow(() -> new ResourceNotFoundException("Subaccount", "id", subaccountId));

        Wallet wallet = Wallet.builder()
                .subaccount(subaccount)
                .build();

        wallet = walletRepository.save(wallet);
        log.info("Wallet created: walletId={}, subaccountId={}", wallet.getId(), subaccountId);

        return WalletMapper.toResponse(wallet);
    }
}
