package com.theron.wallet.service;

import com.theron.wallet.dto.response.DashboardResponse;
import com.theron.wallet.dto.response.MeResponse;
import com.theron.wallet.dto.response.NotificationResponse;
import com.theron.wallet.dto.response.PageResponse;
import com.theron.wallet.dto.response.AccountResponse;
import com.theron.wallet.dto.response.TransactionResponse;
import com.theron.wallet.dto.response.WalletResponse;
import com.theron.wallet.enums.TransactionStatus;
import com.theron.wallet.enums.TransactionType;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public interface MeService {

    MeResponse me(UUID userId);

    DashboardResponse dashboard(UUID userId, UUID organizationId, UUID accountId);

    PageResponse<AccountResponse> accounts(UUID userId, UUID organizationId, Pageable pageable);

    PageResponse<WalletResponse> wallets(UUID userId, UUID organizationId, UUID accountId, Pageable pageable);

    PageResponse<TransactionResponse> transactions(
            UUID userId,
            UUID organizationId,
            UUID accountId,
            LocalDateTime from,
            LocalDateTime to,
            TransactionType type,
            TransactionStatus status,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            Pageable pageable);

    PageResponse<NotificationResponse> notifications(
            UUID userId, UUID organizationId, boolean unreadOnly, Pageable pageable);
}
