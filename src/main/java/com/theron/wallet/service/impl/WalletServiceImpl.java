package com.theron.wallet.service.impl;

import com.theron.wallet.dto.response.WalletResponse;
import com.theron.wallet.entity.Customer;
import com.theron.wallet.entity.Wallet;
import com.theron.wallet.exception.InsufficientBalanceException;
import com.theron.wallet.exception.ResourceNotFoundException;
import com.theron.wallet.mapper.WalletMapper;
import com.theron.wallet.repository.CustomerRepository;
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
    private final CustomerRepository customerRepository;

    @Override
    @Transactional(readOnly = true)
    public WalletResponse findByCustomerId(UUID customerId) {
        Wallet wallet = walletRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet", "customerId", customerId));
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
    public WalletResponse getOrCreateWallet(UUID customerId) {
        return walletRepository.findByCustomerId(customerId)
                .map(WalletMapper::toResponse)
                .orElseGet(() -> createWallet(customerId));
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

    private WalletResponse createWallet(UUID customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", customerId));

        Wallet wallet = Wallet.builder()
                .customer(customer)
                .build();

        wallet = walletRepository.save(wallet);
        log.info("Wallet created: walletId={}, customerId={}", wallet.getId(), customerId);

        return WalletMapper.toResponse(wallet);
    }
}
