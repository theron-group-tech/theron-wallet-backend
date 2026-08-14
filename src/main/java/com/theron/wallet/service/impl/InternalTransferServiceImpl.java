package com.theron.wallet.service.impl;

import com.theron.wallet.dto.request.InternalTransferRequest;
import com.theron.wallet.dto.response.InternalTransferResponse;
import com.theron.wallet.entity.Transaction;
import com.theron.wallet.entity.Wallet;
import com.theron.wallet.enums.TransactionStatus;
import com.theron.wallet.enums.TransactionType;
import com.theron.wallet.exception.InsufficientBalanceException;
import com.theron.wallet.exception.ResourceNotFoundException;
import com.theron.wallet.exception.SelfTransferException;
import com.theron.wallet.mapper.TransactionMapper;
import com.theron.wallet.repository.SubaccountRepository;
import com.theron.wallet.repository.TransactionRepository;
import com.theron.wallet.repository.WalletRepository;
import com.theron.wallet.service.InternalTransferService;
import com.theron.wallet.service.LedgerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InternalTransferServiceImpl implements InternalTransferService {

    private final SubaccountRepository subaccountRepository;
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final LedgerService ledgerService;

    @Override
    @Transactional
    public InternalTransferResponse transfer(InternalTransferRequest request) {
        UUID senderSubaccountId   = request.getSenderSubaccountId();
        UUID receiverSubaccountId = request.getReceiverSubaccountId();

        log.info("Internal transfer requested: sender={}, receiver={}, amount={}",
                senderSubaccountId, receiverSubaccountId, request.getAmount());

        // 1. Reject self-transfers immediately
        if (senderSubaccountId.equals(receiverSubaccountId)) {
            throw new SelfTransferException("Sender and receiver must be different subaccounts");
        }

        // 2. Idempotency check
        String idempotencyKey = request.getIdempotencyKey() != null
                ? request.getIdempotencyKey()
                : UUID.randomUUID().toString();

        Optional<Transaction> existingSenderTx = transactionRepository.findByIdempotencyKey(idempotencyKey);
        if (existingSenderTx.isPresent()) {
            log.info("Idempotency hit for key={} — returning existing transfer result", idempotencyKey);
            Transaction senderTx = existingSenderTx.get();
            Transaction receiverTx = transactionRepository
                    .findByExternalReference(senderTx.getId().toString())
                    .orElse(senderTx);
            return TransactionMapper.toInternalTransferResponse(senderTx, receiverTx);
        }

        // 3. Validate both subaccounts exist
        subaccountRepository.findById(senderSubaccountId)
                .orElseThrow(() -> new ResourceNotFoundException("Subaccount", "id", senderSubaccountId));
        subaccountRepository.findById(receiverSubaccountId)
                .orElseThrow(() -> new ResourceNotFoundException("Subaccount", "id", receiverSubaccountId));

        // 4. Acquire pessimistic write locks in deterministic UUID order (deadlock prevention)
        boolean senderFirst = senderSubaccountId.compareTo(receiverSubaccountId) < 0;
        UUID firstId  = senderFirst ? senderSubaccountId  : receiverSubaccountId;
        UUID secondId = senderFirst ? receiverSubaccountId : senderSubaccountId;

        Wallet firstWallet = walletRepository.findBySubaccountIdWithLock(firstId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet", "subaccountId", firstId));
        Wallet secondWallet = walletRepository.findBySubaccountIdWithLock(secondId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet", "subaccountId", secondId));

        Wallet senderWallet   = senderFirst ? firstWallet  : secondWallet;
        Wallet receiverWallet = senderFirst ? secondWallet : firstWallet;

        // 5. Validate sender balance
        if (senderWallet.getBalance().compareTo(request.getAmount()) < 0) {
            throw new InsufficientBalanceException(
                    String.format("Insufficient balance. Available: %s, Requested: %s",
                            senderWallet.getBalance(), request.getAmount()));
        }

        // 6. Debit sender and credit receiver atomically
        senderWallet.debit(request.getAmount());
        receiverWallet.credit(request.getAmount());
        walletRepository.save(senderWallet);
        walletRepository.save(receiverWallet);

        // 7. Record ledger entries
        Transaction senderTx = transactionRepository.save(Transaction.builder()
                .wallet(senderWallet)
                .type(TransactionType.TRANSFER_OUT)
                .status(TransactionStatus.CONFIRMED)
                .amount(request.getAmount())
                .description(request.getDescription())
                .idempotencyKey(idempotencyKey)
                .build());

        Transaction receiverTx = transactionRepository.save(Transaction.builder()
                .wallet(receiverWallet)
                .type(TransactionType.TRANSFER_IN)
                .status(TransactionStatus.CONFIRMED)
                .amount(request.getAmount())
                .description(request.getDescription())
                .externalReference(senderTx.getId().toString())
                .idempotencyKey(UUID.randomUUID().toString())
                .build());

        if (senderWallet.getAccount() != null && receiverWallet.getAccount() != null) {
            ledgerService.postTransfer(
                    senderWallet.getAccount().getId(),
                    receiverWallet.getAccount().getId(),
                    request.getAmount(),
                    "ledger:transfer:" + idempotencyKey,
                    senderTx.getId().toString());
        }

        log.info("Internal transfer completed: senderTx={}, receiverTx={}, amount={}",
                senderTx.getId(), receiverTx.getId(), request.getAmount());

        return TransactionMapper.toInternalTransferResponse(senderTx, receiverTx);
    }
}
