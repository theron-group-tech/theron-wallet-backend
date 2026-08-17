package com.theron.wallet.service.impl;

import com.theron.wallet.dto.request.InternalTransferRequest;
import com.theron.wallet.dto.response.InternalTransferResponse;
import com.theron.wallet.entity.Account;
import com.theron.wallet.entity.Transaction;
import com.theron.wallet.entity.User;
import com.theron.wallet.entity.Wallet;
import com.theron.wallet.enums.LimitTransactionType;
import com.theron.wallet.enums.TransactionStatus;
import com.theron.wallet.enums.TransactionType;
import com.theron.wallet.exception.ForbiddenException;
import com.theron.wallet.exception.InsufficientBalanceException;
import com.theron.wallet.exception.ResourceNotFoundException;
import com.theron.wallet.exception.SelfTransferException;
import com.theron.wallet.exception.UnauthorizedException;
import com.theron.wallet.mapper.TransactionMapper;
import com.theron.wallet.repository.AccountRepository;
import com.theron.wallet.repository.SubaccountRepository;
import com.theron.wallet.repository.TransactionRepository;
import com.theron.wallet.repository.UserRepository;
import com.theron.wallet.repository.WalletRepository;
import com.theron.wallet.security.PermissionCodes;
import com.theron.wallet.service.AuthorizationService;
import com.theron.wallet.service.IdempotencyService;
import com.theron.wallet.service.InternalTransferService;
import com.theron.wallet.service.LedgerService;
import com.theron.wallet.service.LimitContext;
import com.theron.wallet.service.TransactionLifecycleService;
import com.theron.wallet.service.TransactionLimitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InternalTransferServiceImpl implements InternalTransferService {

    private final SubaccountRepository subaccountRepository;
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final LedgerService ledgerService;
    private final IdempotencyService idempotencyService;
    private final TransactionLifecycleService transactionLifecycleService;
    private final AuthorizationService authorizationService;
    private final TransactionLimitService transactionLimitService;
    private final PlatformTransactionManager transactionManager;

    @Override
    public InternalTransferResponse transfer(UUID actorUserId, InternalTransferRequest request) {
        if (actorUserId == null) {
            throw new UnauthorizedException("Authentication required");
        }
        UUID senderSubaccountId = request.getSenderSubaccountId();
        UUID receiverSubaccountId = request.getReceiverSubaccountId();

        log.info("Internal transfer requested: sender={}, receiver={}, amount={}",
                senderSubaccountId, receiverSubaccountId, request.getAmount());

        if (senderSubaccountId.equals(receiverSubaccountId)) {
            throw new SelfTransferException("Sender and receiver must be different subaccounts");
        }

        String idempotencyKey = idempotencyService.resolveKey(null, request.getIdempotencyKey());
        String requestHash = idempotencyService.hash(
                TransactionType.TRANSFER_OUT.name(),
                senderSubaccountId.toString(),
                receiverSubaccountId.toString(),
                idempotencyService.amountPart(request.getAmount()),
                "BRL");

        return idempotencyService.findExisting(idempotencyKey, requestHash)
                .map(this::toResponse)
                .orElseGet(() -> createNewTransfer(actorUserId, request, idempotencyKey, requestHash));
    }

    private InternalTransferResponse createNewTransfer(
            UUID actorUserId, InternalTransferRequest request, String idempotencyKey, String requestHash) {
        try {
            TransactionTemplate tx = new TransactionTemplate(transactionManager);
            return tx.execute(status -> persistTransfer(actorUserId, request, idempotencyKey, requestHash));
        } catch (RuntimeException ex) {
            if (!ProviderCall.isUniqueConstraint(ex)) {
                throw ex;
            }
            return toResponse(idempotencyService.requireExisting(idempotencyKey, requestHash));
        }
    }

    private InternalTransferResponse persistTransfer(
            UUID actorUserId, InternalTransferRequest request, String idempotencyKey, String requestHash) {
        UUID senderSubaccountId = request.getSenderSubaccountId();
        UUID receiverSubaccountId = request.getReceiverSubaccountId();

        subaccountRepository.findById(senderSubaccountId)
                .orElseThrow(() -> new ResourceNotFoundException("Subaccount", "id", senderSubaccountId));
        subaccountRepository.findById(receiverSubaccountId)
                .orElseThrow(() -> new ResourceNotFoundException("Subaccount", "id", receiverSubaccountId));

        boolean senderFirst = senderSubaccountId.compareTo(receiverSubaccountId) < 0;
        UUID firstId = senderFirst ? senderSubaccountId : receiverSubaccountId;
        UUID secondId = senderFirst ? receiverSubaccountId : senderSubaccountId;

        Wallet firstWallet = walletRepository.findBySubaccountIdWithLock(firstId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet", "subaccountId", firstId));
        Wallet secondWallet = walletRepository.findBySubaccountIdWithLock(secondId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet", "subaccountId", secondId));

        Wallet senderWallet = senderFirst ? firstWallet : secondWallet;
        Wallet receiverWallet = senderFirst ? secondWallet : firstWallet;

        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", actorUserId));
        authorizeAndLimitIfAccountPresent(senderWallet, actor, request.getAmount());

        if (senderWallet.getBalance().compareTo(request.getAmount()) < 0) {
            throw new InsufficientBalanceException(
                    String.format("Insufficient balance. Available: %s, Requested: %s",
                            senderWallet.getBalance(), request.getAmount()));
        }

        senderWallet.debit(request.getAmount());
        receiverWallet.credit(request.getAmount());
        walletRepository.save(senderWallet);
        walletRepository.save(receiverWallet);

        Transaction.TransactionBuilder senderBuilder = Transaction.builder()
                .wallet(senderWallet)
                .type(TransactionType.TRANSFER_OUT)
                .status(TransactionStatus.PROCESSING)
                .amount(request.getAmount())
                .description(request.getDescription())
                .reference(ProviderCall.referenceOf(request.getDescription()))
                .idempotencyKey(idempotencyKey)
                .requestHash(requestHash)
                .createdBy(actor);
        idempotencyService.applyOwner(senderBuilder, senderWallet);
        Transaction senderTx = transactionRepository.save(senderBuilder.build());
        senderTx = transactionLifecycleService.transition(senderTx, TransactionStatus.COMPLETED);

        Transaction.TransactionBuilder receiverBuilder = Transaction.builder()
                .wallet(receiverWallet)
                .type(TransactionType.TRANSFER_IN)
                .status(TransactionStatus.PROCESSING)
                .amount(request.getAmount())
                .description(request.getDescription())
                .reference(ProviderCall.referenceOf(request.getDescription()))
                .externalReference(senderTx.getId().toString())
                .idempotencyKey(UUID.randomUUID().toString())
                .createdBy(actor);
        idempotencyService.applyOwner(receiverBuilder, receiverWallet);
        Transaction receiverTx = transactionRepository.save(receiverBuilder.build());
        receiverTx = transactionLifecycleService.transition(receiverTx, TransactionStatus.COMPLETED);

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

    private InternalTransferResponse toResponse(Transaction senderTx) {
        Transaction receiverTx = transactionRepository
                .findByExternalReference(senderTx.getId().toString())
                .orElse(senderTx);
        return TransactionMapper.toInternalTransferResponse(senderTx, receiverTx);
    }

    private void authorizeAndLimitIfAccountPresent(Wallet wallet, User actor, java.math.BigDecimal amount) {
        Account account = wallet.getAccount();
        if (account == null) {
            return;
        }
        Account managed = accountRepository.findByIdWithOrganization(account.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Account", "id", account.getId()));
        UUID orgId = managed.getOrganization().getId();
        if (!authorizationService.hasPermission(orgId, actor.getId(), PermissionCodes.WALLET_TRANSFER)
                && !authorizationService.hasPermission(orgId, actor.getId(), PermissionCodes.TRANSACTIONS_CREATE)) {
            throw new ForbiddenException("Missing wallet.transfer or transactions.create");
        }
        transactionLimitService.assertWithinLimits(new LimitContext(
                orgId,
                managed.getId(),
                actor.getId(),
                LimitTransactionType.TRANSFER,
                amount,
                null));
    }
}
