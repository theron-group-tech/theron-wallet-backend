package com.theron.wallet.service;

import com.theron.wallet.dto.response.PageResponse;
import com.theron.wallet.dto.response.TransactionResponse;
import com.theron.wallet.enums.TransactionStatus;
import com.theron.wallet.enums.TransactionType;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.UUID;

public interface StatementService {

    PageResponse<TransactionResponse> list(
            Collection<UUID> accountIds,
            LocalDateTime from,
            LocalDateTime to,
            TransactionType type,
            TransactionStatus status,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            Pageable pageable);

    PageResponse<TransactionResponse> statement(
            UUID userId,
            UUID accountId,
            LocalDateTime from,
            LocalDateTime to,
            TransactionType type,
            TransactionStatus status,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            Pageable pageable);
}
