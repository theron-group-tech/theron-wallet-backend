package com.theron.wallet.service.impl;

import com.theron.wallet.config.AccountLimitProperties;
import com.theron.wallet.entity.AccountLimit;
import com.theron.wallet.exception.InvalidRequestException;
import com.theron.wallet.repository.AccountLimitRepository;
import com.theron.wallet.repository.TransactionRepository;
import com.theron.wallet.service.AccountLimitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountLimitServiceImpl implements AccountLimitService {

    private final AccountLimitRepository accountLimitRepository;
    private final TransactionRepository transactionRepository;
    private final AccountLimitProperties accountLimitProperties;

    @Override
    @Transactional
    public void ensureDefaults(UUID accountId) {
        if (accountId == null) {
            throw new InvalidRequestException("Conta sem limite financeiro configurado.");
        }
        if (accountLimitRepository.findByAccountId(accountId).isPresent()) {
            return;
        }
        try {
            accountLimitRepository.saveAndFlush(AccountLimit.builder()
                    .accountId(accountId)
                    .maxOperationAmount(accountLimitProperties.getDefaultMaxOperation())
                    .dailyLimitAmount(accountLimitProperties.getDefaultDaily())
                    .build());
            log.info("Provisioned default account_limit for accountId={}", accountId);
        } catch (DataIntegrityViolationException ex) {
            // Concurrent create — row now exists
            if (accountLimitRepository.findByAccountId(accountId).isEmpty()) {
                throw new InvalidRequestException("Conta sem limite financeiro configurado.");
            }
        }
    }

    @Override
    @Transactional
    public void assertWithinLimits(UUID accountId, BigDecimal amount) {
        assertWithinLimits(accountId, amount, null);
    }

    @Override
    @Transactional
    public void assertWithinLimits(UUID accountId, BigDecimal amount, UUID excludeTransactionId) {
        ensureDefaults(accountId);

        AccountLimit limit = accountLimitRepository.findByAccountIdForUpdate(accountId)
                .orElseThrow(() -> new InvalidRequestException(
                        "Conta sem limite financeiro configurado."));

        if (amount.compareTo(limit.getMaxOperationAmount()) > 0) {
            throw new InvalidRequestException(String.format(
                    "Amount exceeds max operation limit. Limit: %s, Requested: %s",
                    limit.getMaxOperationAmount(), amount));
        }

        LocalDate today = LocalDate.now();
        LocalDateTime dayStart = today.atStartOfDay();
        LocalDateTime dayEnd = today.plusDays(1).atStartOfDay();
        BigDecimal spent = transactionRepository.sumPixAmountForAccountOnDay(
                accountId, dayStart, dayEnd, excludeTransactionId);
        if (spent == null) {
            spent = BigDecimal.ZERO;
        }

        BigDecimal projected = spent.add(amount);
        if (projected.compareTo(limit.getDailyLimitAmount()) > 0) {
            throw new InvalidRequestException(String.format(
                    "Amount exceeds daily limit. Daily limit: %s, Already used: %s, Requested: %s",
                    limit.getDailyLimitAmount(), spent, amount));
        }

        log.debug("Limits OK accountId={}, amount={}, spentToday={}, dailyLimit={}",
                accountId, amount, spent, limit.getDailyLimitAmount());
    }
}
