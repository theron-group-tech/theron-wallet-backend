package com.theron.wallet.service;

import com.theron.wallet.dto.response.TransactionResponse;
import com.theron.wallet.enums.TransactionStatus;
import com.theron.wallet.enums.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface TransactionService {

    TransactionResponse findById(UUID transactionId);

    Page<TransactionResponse> findByWalletId(UUID walletId, Pageable pageable);

    Page<TransactionResponse> findByWalletIdAndType(UUID walletId, TransactionType type, Pageable pageable);

    Page<TransactionResponse> findByWalletIdAndStatus(UUID walletId, TransactionStatus status, Pageable pageable);

    /** All transactions for a subaccount, with optional type filter. */
    Page<TransactionResponse> findBySubaccountId(UUID subaccountId, TransactionType type, Pageable pageable);

    /** Global listing — optional walletId, subaccountId, and/or type filter. */
    Page<TransactionResponse> findAll(UUID walletId, UUID subaccountId, TransactionType type, Pageable pageable);
}
