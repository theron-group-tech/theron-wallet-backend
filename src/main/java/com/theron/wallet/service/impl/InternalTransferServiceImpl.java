package com.theron.wallet.service.impl;

import com.theron.wallet.dto.request.InternalTransferRequest;
import com.theron.wallet.dto.response.InternalTransferResponse;
import com.theron.wallet.entity.Customer;
import com.theron.wallet.entity.Transaction;
import com.theron.wallet.entity.Wallet;
import com.theron.wallet.enums.TransactionStatus;
import com.theron.wallet.enums.TransactionType;
import com.theron.wallet.exception.InsufficientBalanceException;
import com.theron.wallet.exception.ResourceNotFoundException;
import com.theron.wallet.exception.SelfTransferException;
import com.theron.wallet.mapper.TransactionMapper;
import com.theron.wallet.repository.CustomerRepository;
import com.theron.wallet.repository.TransactionRepository;
import com.theron.wallet.repository.WalletRepository;
import com.theron.wallet.service.InternalTransferService;
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

    private final CustomerRepository customerRepository;
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;

    @Override
    @Transactional
    public InternalTransferResponse transfer(InternalTransferRequest request) {
        log.info("Internal transfer requested: sender={}, receiver={}, amount={}",
                request.getSenderCustomerId(), request.getReceiverCustomerId(), request.getAmount());

        // 1. Reject self-transfers immediately — no DB access needed
        if (request.getSenderCustomerId().equals(request.getReceiverCustomerId())) {
            throw new SelfTransferException("Sender and receiver must be different customers");
        }

        // 2. Idempotency check — keyed on the sender-side transaction
        String idempotencyKey = request.getIdempotencyKey() != null
                ? request.getIdempotencyKey()
                : UUID.randomUUID().toString();

        Optional<Transaction> existingSenderTx = transactionRepository.findByIdempotencyKey(idempotencyKey);
        if (existingSenderTx.isPresent()) {
            log.info("Idempotency hit for key={} — returning existing transfer result", idempotencyKey);
            Transaction senderTx = existingSenderTx.get();
            // Receiver tx is linked via externalReference = senderTx.getId()
            Transaction receiverTx = transactionRepository
                    .findByExternalReference(senderTx.getId().toString())
                    .orElseGet(() -> {
                        log.error("Data integrity issue: receiver transaction missing for senderTx={} (idempotencyKey={}). "
                                + "The original transfer may have been only partially committed.",
                                senderTx.getId(), idempotencyKey);
                        return senderTx;
                    });
            return TransactionMapper.toInternalTransferResponse(senderTx, receiverTx);
        }

        // 3. Validate both customers exist
        Customer sender = customerRepository.findById(request.getSenderCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", request.getSenderCustomerId()));
        Customer receiver = customerRepository.findById(request.getReceiverCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", request.getReceiverCustomerId()));

        UUID senderCustomerId = sender.getId();
        UUID receiverCustomerId = receiver.getId();

        // 4. Acquire pessimistic write locks in deterministic customer-UUID order.
        //    Ordering by a value known BEFORE any wallet load is critical: it avoids going through
        //    the JPA L1 (session) cache, so Hibernate always hits the DB for a truly fresh row.
        //    If we loaded wallets unlocked first and then re-locked them, Hibernate would serve
        //    the cached (stale) entity back — causing lost updates under concurrency.
        boolean senderFirst = senderCustomerId.compareTo(receiverCustomerId) < 0;
        UUID firstCustId  = senderFirst ? senderCustomerId  : receiverCustomerId;
        UUID secondCustId = senderFirst ? receiverCustomerId : senderCustomerId;

        Wallet firstWallet = walletRepository.findByCustomerIdWithLock(firstCustId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet", "customerId", firstCustId));
        Wallet secondWallet = walletRepository.findByCustomerIdWithLock(secondCustId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet", "customerId", secondCustId));

        // 5. Resolve sender/receiver from the locked pair using the pre-computed order flag.
        //    No lazy-load of wallet.customer needed — we rely purely on customer IDs we already have.
        Wallet senderWallet   = senderFirst ? firstWallet  : secondWallet;
        Wallet receiverWallet = senderFirst ? secondWallet : firstWallet;

        // 6. Validate sender has sufficient balance
        if (senderWallet.getBalance().compareTo(request.getAmount()) < 0) {
            throw new InsufficientBalanceException(
                    String.format("Insufficient balance. Available: %s, Requested: %s",
                            senderWallet.getBalance(), request.getAmount()));
        }

        // 7. Debit sender and credit receiver
        senderWallet.debit(request.getAmount());
        receiverWallet.credit(request.getAmount());
        walletRepository.save(senderWallet);
        walletRepository.save(receiverWallet);

        // 8. Record TRANSFER_OUT for sender (carries the client idempotency key)
        Transaction senderTx = Transaction.builder()
                .wallet(senderWallet)
                .type(TransactionType.TRANSFER_OUT)
                .status(TransactionStatus.CONFIRMED)
                .amount(request.getAmount())
                .description(request.getDescription())
                .idempotencyKey(idempotencyKey)
                .build();
        senderTx = transactionRepository.save(senderTx);

        // 9. Record TRANSFER_IN for receiver, linked to the sender tx via externalReference
        Transaction receiverTx = Transaction.builder()
                .wallet(receiverWallet)
                .type(TransactionType.TRANSFER_IN)
                .status(TransactionStatus.CONFIRMED)
                .amount(request.getAmount())
                .description(request.getDescription())
                .externalReference(senderTx.getId().toString())
                .idempotencyKey(UUID.randomUUID().toString()) // independent idempotency key for receiver side
                .build();
        receiverTx = transactionRepository.save(receiverTx);

        log.info("Internal transfer completed: senderTx={}, receiverTx={}, amount={}",
                senderTx.getId(), receiverTx.getId(), request.getAmount());

        return TransactionMapper.toInternalTransferResponse(senderTx, receiverTx);
    }
}
