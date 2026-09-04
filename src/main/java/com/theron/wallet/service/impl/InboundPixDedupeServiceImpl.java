package com.theron.wallet.service.impl;

import com.theron.wallet.dto.response.InboundDedupeResponse;
import com.theron.wallet.entity.Account;
import com.theron.wallet.entity.PlatformPixTransfer;
import com.theron.wallet.entity.PixKey;
import com.theron.wallet.entity.Transaction;
import com.theron.wallet.entity.Wallet;
import com.theron.wallet.enums.PixKeyStatus;
import com.theron.wallet.enums.TransactionStatus;
import com.theron.wallet.enums.TransactionType;
import com.theron.wallet.exception.ResourceNotFoundException;
import com.theron.wallet.repository.AccountRepository;
import com.theron.wallet.repository.PixKeyRepository;
import com.theron.wallet.repository.PlatformPixTransferRepository;
import com.theron.wallet.repository.TransactionRepository;
import com.theron.wallet.repository.WalletRepository;
import com.theron.wallet.service.InboundPixDedupeService;
import com.theron.wallet.service.TransactionLifecycleService;
import com.theron.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InboundPixDedupeServiceImpl implements InboundPixDedupeService {

    private static final String ORPHAN_PREFIX = "asaas:pix:in:pay_%";

    private final AccountRepository accountRepository;
    private final PixKeyRepository pixKeyRepository;
    private final PlatformPixTransferRepository platformPixTransferRepository;
    private final TransactionRepository transactionRepository;
    private final WalletRepository walletRepository;
    private final WalletService walletService;
    private final TransactionLifecycleService transactionLifecycleService;

    @Override
    @Transactional
    public InboundDedupeResponse dedupeAccount(UUID accountId) {
        Account account = accountRepository.findByIdWithOrganization(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", "id", accountId));

        List<PixKey> keys = pixKeyRepository.findByAccountIdOrderByCreatedAtDesc(accountId);
        List<String> destinationKeys = keys.stream()
                .filter(k -> k.getStatus() == PixKeyStatus.ACTIVE)
                .map(PixKey::getKey)
                .filter(k -> k != null && !k.isBlank())
                .toList();

        List<PlatformPixTransfer> platformCredits = destinationKeys.isEmpty()
                ? List.of()
                : platformPixTransferRepository.findCreditedByDestinationKeys(
                        destinationKeys, TransactionStatus.COMPLETED);

        List<Transaction> orphans = transactionRepository.findByAccountTypeStatusAndIdempotencyPrefix(
                accountId,
                TransactionType.TRANSFER_IN,
                TransactionStatus.COMPLETED,
                ORPHAN_PREFIX);

        List<InboundDedupeResponse.Item> items = new ArrayList<>();
        int reversed = 0;
        int skipped = 0;
        BigDecimal totalDebited = BigDecimal.ZERO;

        // One consumable slot per Master→Theron platform credit (by amount)
        List<BigDecimal> unmatchedPlatformAmounts = new ArrayList<>();
        for (PlatformPixTransfer row : platformCredits) {
            unmatchedPlatformAmounts.add(row.getAmount());
        }

        for (Transaction orphan : orphans) {
            boolean looksAuto = PlatformPixInboundDedupe.looksLikeAutoChargeFromReceivedPix(
                    orphan.getDescription());
            if (!looksAuto) {
                skipped++;
                items.add(skipItem(orphan, "Description does not look like Master→Theron auto-charge"));
                continue;
            }
            if (!hasMatchingAmount(unmatchedPlatformAmounts, orphan.getAmount())) {
                skipped++;
                items.add(skipItem(orphan, "No unmatched platform_pix credit slot for this amount"));
                continue;
            }

            Transaction locked = transactionRepository.findByIdForUpdate(orphan.getId())
                    .orElse(orphan);
            if (locked.getStatus() != TransactionStatus.COMPLETED) {
                skipped++;
                items.add(skipItem(locked, "Transaction no longer COMPLETED"));
                continue;
            }

            Wallet wallet = locked.getWallet();
            if (wallet == null) {
                wallet = walletRepository.findByAccountId(accountId).orElse(null);
            }
            if (wallet == null) {
                skipped++;
                items.add(skipItem(locked, "Wallet not found"));
                continue;
            }

            if (!consumeMatchingAmount(unmatchedPlatformAmounts, locked.getAmount())) {
                skipped++;
                items.add(skipItem(locked, "No unmatched platform_pix credit slot for this amount"));
                continue;
            }

            walletService.debit(wallet.getId(), locked.getAmount());
            transactionLifecycleService.transition(locked, TransactionStatus.REVERSED);
            locked.setDescription((locked.getDescription() != null ? locked.getDescription() + " | " : "")
                    + "REVERSED: duplicate Master→Theron inbound (platform_pix already credited)");
            transactionRepository.save(locked);

            reversed++;
            totalDebited = totalDebited.add(locked.getAmount());
            items.add(InboundDedupeResponse.Item.builder()
                    .transactionId(locked.getId())
                    .idempotencyKey(locked.getIdempotencyKey())
                    .asaasPaymentId(locked.getAsaasPaymentId())
                    .amount(locked.getAmount())
                    .status("REVERSED")
                    .message("Debited wallet and marked REVERSED")
                    .build());
            log.info("Reversed duplicate inbound PIX: transactionId={}, accountId={}, amount={}",
                    locked.getId(), accountId, locked.getAmount());
        }

        return InboundDedupeResponse.builder()
                .accountId(account.getId())
                .scanned(orphans.size())
                .reversed(reversed)
                .skipped(skipped)
                .totalDebited(totalDebited)
                .items(items)
                .build();
    }

    private static InboundDedupeResponse.Item skipItem(Transaction tx, String message) {
        return InboundDedupeResponse.Item.builder()
                .transactionId(tx.getId())
                .idempotencyKey(tx.getIdempotencyKey())
                .asaasPaymentId(tx.getAsaasPaymentId())
                .amount(tx.getAmount())
                .status("SKIPPED")
                .message(message)
                .build();
    }

    private static boolean hasMatchingAmount(List<BigDecimal> amounts, BigDecimal target) {
        if (target == null) {
            return false;
        }
        for (BigDecimal candidate : amounts) {
            if (candidate != null && candidate.compareTo(target) == 0) {
                return true;
            }
        }
        return false;
    }

    private static boolean consumeMatchingAmount(List<BigDecimal> amounts, BigDecimal target) {
        if (target == null) {
            return false;
        }
        Iterator<BigDecimal> it = amounts.iterator();
        while (it.hasNext()) {
            BigDecimal candidate = it.next();
            if (candidate != null && candidate.compareTo(target) == 0) {
                it.remove();
                return true;
            }
        }
        return false;
    }
}
