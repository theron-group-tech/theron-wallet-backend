package com.theron.wallet.service.impl;

import com.theron.wallet.dto.response.TransactionResponse;
import com.theron.wallet.entity.Transaction;
import com.theron.wallet.enums.TransactionStatus;
import com.theron.wallet.enums.TransactionType;
import com.theron.wallet.exception.ResourceNotFoundException;
import com.theron.wallet.mapper.TransactionMapper;
import com.theron.wallet.repository.TransactionRepository;
import com.theron.wallet.service.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionServiceImpl implements TransactionService {

    private final TransactionRepository transactionRepository;

    @Override
    @Transactional(readOnly = true)
    public TransactionResponse findById(UUID transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", "id", transactionId));
        return TransactionMapper.toResponse(transaction);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TransactionResponse> findByWalletId(UUID walletId, Pageable pageable) {
        return transactionRepository.findByWalletId(walletId, pageable)
                .map(TransactionMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TransactionResponse> findByWalletIdAndType(UUID walletId, TransactionType type, Pageable pageable) {
        return transactionRepository.findByWalletIdAndType(walletId, type, pageable)
                .map(TransactionMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TransactionResponse> findByWalletIdAndStatus(UUID walletId, TransactionStatus status, Pageable pageable) {
        return transactionRepository.findByWalletIdAndStatus(walletId, status, pageable)
                .map(TransactionMapper::toResponse);
    }
}
