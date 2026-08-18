package com.theron.wallet.service.impl;

import com.theron.wallet.dto.response.PageResponse;
import com.theron.wallet.dto.response.TransactionResponse;
import com.theron.wallet.enums.TransactionStatus;
import com.theron.wallet.enums.TransactionType;
import com.theron.wallet.exception.InvalidRequestException;
import com.theron.wallet.mapper.TransactionMapper;
import com.theron.wallet.repository.TransactionRepository;
import com.theron.wallet.repository.TransactionSpecifications;
import com.theron.wallet.service.MobileScopeService;
import com.theron.wallet.service.StatementService;
import com.theron.wallet.web.MobilePageables;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StatementServiceImpl implements StatementService {

    private final TransactionRepository transactionRepository;
    private final MobileScopeService mobileScopeService;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> list(
            Collection<UUID> accountIds,
            LocalDateTime from,
            LocalDateTime to,
            TransactionType type,
            TransactionStatus status,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            Pageable pageable) {
        validateRange(from, to);
        Pageable clamped = MobilePageables.clamp(pageable);
        if (accountIds == null || accountIds.isEmpty()) {
            return PageResponse.from(Page.empty(clamped));
        }
        Page<TransactionResponse> page = transactionRepository.findAll(
                        TransactionSpecifications.statement(
                                accountIds, from, to, type, status, minAmount, maxAmount),
                        clamped)
                .map(TransactionMapper::toMobileResponse);
        return PageResponse.from(page);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> statement(
            UUID userId,
            UUID accountId,
            LocalDateTime from,
            LocalDateTime to,
            TransactionType type,
            TransactionStatus status,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            Pageable pageable) {
        List<UUID> accountIds = mobileScopeService.accountIds(userId, null, accountId);
        return list(accountIds, from, to, type, status, minAmount, maxAmount, pageable);
    }

    static void validateRange(LocalDateTime from, LocalDateTime to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidRequestException("'from' must be before or equal to 'to'");
        }
    }
}
