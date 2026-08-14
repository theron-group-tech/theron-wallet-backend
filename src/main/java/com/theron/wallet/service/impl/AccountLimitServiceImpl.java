package com.theron.wallet.service.impl;

import com.theron.wallet.entity.AccountLimit;
import com.theron.wallet.exception.InvalidRequestException;
import com.theron.wallet.repository.AccountLimitRepository;
import com.theron.wallet.repository.TransactionRepository;
import com.theron.wallet.service.AccountLimitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    @Override
    @Transactional
    public void assertWithinLimits(UUID accountId, BigDecimal amount) {
        AccountLimit limit = accountLimitRepository.findByAccountIdForUpdate(accountId)
                .orElseThrow(() -> new InvalidRequestException(
                        "Account has no financial limits configured"));

        if (amount.compareTo(limit.getMaxOperationAmount()) > 0) {
            throw new InvalidRequestException(String.format(
                    "Amount exceeds max operation limit. Limit: %s, Requested: %s",
                    limit.getMaxOperationAmount(), amount));
        }

        LocalDate today = LocalDate.now();
        LocalDateTime dayStart = today.atStartOfDay();
        LocalDateTime dayEnd = today.plusDays(1).atStartOfDay();
        BigDecimal spent = transactionRepository.sumPixAmountForAccountOnDay(accountId, dayStart, dayEnd);
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
