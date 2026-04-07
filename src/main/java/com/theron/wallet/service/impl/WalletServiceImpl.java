package com.theron.wallet.service.impl;

import com.theron.wallet.dto.response.WalletResponse;
import com.theron.wallet.entity.Subaccount;
import com.theron.wallet.entity.Wallet;
import com.theron.wallet.exception.InsufficientBalanceException;
import com.theron.wallet.exception.ResourceNotFoundException;
import com.theron.wallet.mapper.WalletMapper;
import com.theron.wallet.repository.SubaccountRepository;
import com.theron.wallet.repository.WalletRepository;
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

    @Override
    @Transactional(readOnly = true)
    public WalletResponse findBySubaccountId(UUID subaccountId) {
        Wallet wallet = walletRepository.findBySubaccountId(subaccountId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet", "subaccountId", subaccountId));
        return WalletMapper.toResponse(wallet);
    }

    @Override
    @Transactional(readOnly = true)
    public WalletResponse findById(UUID walletId) {
        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet", "id", walletId));
        return WalletMapper.toResponse(wallet);
    }

    @Override
    @Transactional
    public WalletResponse getOrCreateWallet(UUID subaccountId) {
        return walletRepository.findBySubaccountId(subaccountId)
                .map(WalletMapper::toResponse)
                .orElseGet(() -> createWallet(subaccountId));
    }

    @Override
    @Transactional
    public void credit(UUID walletId, BigDecimal amount) {
        Wallet wallet = walletRepository.findByIdForUpdate(walletId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet", "id", walletId));

        wallet.credit(amount);
        walletRepository.save(wallet);

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

        log.info("Wallet debited: walletId={}, amount={}, newBalance={}", walletId, amount, wallet.getBalance());
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
