package com.theron.wallet.service.impl;

import com.theron.wallet.dto.ledger.LedgerEntryDraft;
import com.theron.wallet.dto.ledger.LedgerPostingRequest;
import com.theron.wallet.dto.response.LedgerBalanceResponse;
import com.theron.wallet.dto.response.LedgerTransactionResponse;
import com.theron.wallet.entity.Account;
import com.theron.wallet.entity.LedgerAccount;
import com.theron.wallet.entity.LedgerEntry;
import com.theron.wallet.entity.LedgerTransaction;
import com.theron.wallet.entity.Organization;
import com.theron.wallet.enums.LedgerAccountKind;
import com.theron.wallet.enums.LedgerDirection;
import com.theron.wallet.enums.LedgerTransactionStatus;
import com.theron.wallet.enums.LedgerTransactionType;
import com.theron.wallet.exception.InsufficientBalanceException;
import com.theron.wallet.exception.InvalidRequestException;
import com.theron.wallet.exception.ResourceNotFoundException;
import com.theron.wallet.repository.AccountRepository;
import com.theron.wallet.repository.LedgerAccountRepository;
import com.theron.wallet.repository.LedgerEntryRepository;
import com.theron.wallet.repository.LedgerTransactionRepository;
import com.theron.wallet.repository.OrganizationRepository;
import com.theron.wallet.service.LedgerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LedgerServiceImpl implements LedgerService {

    private final LedgerAccountRepository ledgerAccountRepository;
    private final LedgerTransactionRepository ledgerTransactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final AccountRepository accountRepository;
    private final OrganizationRepository organizationRepository;

    @Override
    @Transactional
    public void provisionForAccount(Account account) {
        if (ledgerAccountRepository.findByAccount_Id(account.getId()).isEmpty()) {
            ledgerAccountRepository.save(LedgerAccount.builder()
                    .account(account)
                    .organization(account.getOrganization())
                    .kind(LedgerAccountKind.CUSTOMER)
                    .currency(account.getCurrency())
                    .build());
        }
        ensureClearing(account.getOrganization().getId(), account.getCurrency());
        log.info("Ledger provisioned: accountId={}, organizationId={}",
                account.getId(), account.getOrganization().getId());
    }

    @Override
    @Transactional
    public LedgerTransactionResponse post(LedgerPostingRequest request) {
        String idempotencyKey = requireIdempotencyKey(request.idempotencyKey());
        return ledgerTransactionRepository.findByIdempotencyKey(idempotencyKey)
                .map(this::toResponse)
                .orElseGet(() -> persistPosting(request, idempotencyKey));
    }

    @Override
    @Transactional
    public LedgerTransactionResponse postCredit(
            UUID accountId, BigDecimal amount, String idempotencyKey, String reference) {
        LedgerAccount customer = customerLedger(accountId);
        LedgerAccount clearing = ensureClearing(customer.getOrganization().getId(), customer.getCurrency());
        return post(new LedgerPostingRequest(
                idempotencyKey,
                LedgerTransactionType.CREDIT,
                reference,
                List.of(
                        new LedgerEntryDraft(clearing.getId(), LedgerDirection.DEBIT, amount),
                        new LedgerEntryDraft(customer.getId(), LedgerDirection.CREDIT, amount)
                )));
    }

    @Override
    @Transactional
    public LedgerTransactionResponse postDebit(
            UUID accountId, BigDecimal amount, String idempotencyKey, String reference) {
        LedgerAccount customer = customerLedger(accountId);
        LedgerAccount clearing = ensureClearing(customer.getOrganization().getId(), customer.getCurrency());
        return post(new LedgerPostingRequest(
                idempotencyKey,
                LedgerTransactionType.DEBIT,
                reference,
                List.of(
                        new LedgerEntryDraft(customer.getId(), LedgerDirection.DEBIT, amount),
                        new LedgerEntryDraft(clearing.getId(), LedgerDirection.CREDIT, amount)
                )));
    }

    @Override
    @Transactional
    public LedgerTransactionResponse postTransfer(
            UUID fromAccountId, UUID toAccountId, BigDecimal amount, String idempotencyKey, String reference) {
        if (fromAccountId.equals(toAccountId)) {
            throw new InvalidRequestException("Transfer accounts must be different");
        }
        LedgerAccount from = customerLedger(fromAccountId);
        LedgerAccount to = customerLedger(toAccountId);
        if (!from.getCurrency().equals(to.getCurrency())) {
            throw new InvalidRequestException("Transfer currency mismatch");
        }
        return post(new LedgerPostingRequest(
                idempotencyKey,
                LedgerTransactionType.TRANSFER,
                reference,
                List.of(
                        new LedgerEntryDraft(from.getId(), LedgerDirection.DEBIT, amount),
                        new LedgerEntryDraft(to.getId(), LedgerDirection.CREDIT, amount)
                )));
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal reconstructBalance(UUID accountId) {
        customerLedger(accountId);
        BigDecimal balance = ledgerEntryRepository.reconstructBalanceByAccountId(accountId);
        return normalizeZero(balance);
    }

    @Override
    @Transactional(readOnly = true)
    public LedgerBalanceResponse getBalance(UUID accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", "id", accountId));
        return LedgerBalanceResponse.builder()
                .accountId(accountId)
                .currency(account.getCurrency())
                .balance(reconstructBalance(accountId))
                .build();
    }

    private LedgerTransactionResponse persistPosting(LedgerPostingRequest request, String idempotencyKey) {
        List<LedgerEntryDraft> drafts = request.entries();
        if (drafts == null || drafts.size() < 2) {
            throw new InvalidRequestException("Ledger transaction must have at least two entries");
        }
        if (request.type() == null) {
            throw new InvalidRequestException("Ledger transaction type is required");
        }

        List<NormalizedDraft> normalized = drafts.stream()
                .map(this::normalizeDraft)
                .toList();

        BigDecimal debitSum = sum(normalized, LedgerDirection.DEBIT);
        BigDecimal creditSum = sum(normalized, LedgerDirection.CREDIT);
        if (debitSum.compareTo(creditSum) != 0) {
            throw new InvalidRequestException("SUM(DEBIT) must equal SUM(CREDIT)");
        }

        List<UUID> orderedIds = normalized.stream()
                .map(NormalizedDraft::ledgerAccountId)
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
        Map<UUID, LedgerAccount> byId = new HashMap<>();
        for (UUID id : orderedIds) {
            LedgerAccount locked = ledgerAccountRepository.findByIdForUpdate(id)
                    .orElseThrow(() -> new ResourceNotFoundException("LedgerAccount", "id", id));
            byId.put(id, locked);
        }

        String currency = null;
        for (UUID id : orderedIds) {
            LedgerAccount ledgerAccount = byId.get(id);
            if (currency == null) {
                currency = ledgerAccount.getCurrency();
            } else if (!currency.equals(ledgerAccount.getCurrency())) {
                throw new InvalidRequestException("All ledger entries must share the same currency");
            }
        }

        Map<UUID, BigDecimal> deltaByAccount = new HashMap<>();
        for (NormalizedDraft draft : normalized) {
            BigDecimal signed = draft.direction() == LedgerDirection.CREDIT ? draft.amount() : draft.amount().negate();
            deltaByAccount.merge(draft.ledgerAccountId(), signed, BigDecimal::add);
        }
        for (Map.Entry<UUID, BigDecimal> delta : deltaByAccount.entrySet()) {
            LedgerAccount ledgerAccount = byId.get(delta.getKey());
            if (ledgerAccount.getKind() != LedgerAccountKind.CUSTOMER) {
                continue;
            }
            BigDecimal current = normalizeZero(
                    ledgerEntryRepository.reconstructBalanceByLedgerAccountId(ledgerAccount.getId()));
            if (current.add(delta.getValue()).compareTo(BigDecimal.ZERO) < 0) {
                throw new InsufficientBalanceException(
                        String.format("Insufficient ledger balance. Available: %s", current));
            }
        }

        LedgerTransaction transaction = ledgerTransactionRepository.save(LedgerTransaction.builder()
                .reference(request.reference())
                .type(request.type())
                .status(LedgerTransactionStatus.POSTED)
                .idempotencyKey(idempotencyKey)
                .build());

        List<LedgerEntry> savedEntries = new ArrayList<>();
        for (NormalizedDraft draft : normalized) {
            savedEntries.add(ledgerEntryRepository.save(LedgerEntry.builder()
                    .transaction(transaction)
                    .ledgerAccount(byId.get(draft.ledgerAccountId()))
                    .direction(draft.direction())
                    .amount(draft.amount())
                    .build()));
        }

        log.info("Ledger posted: ledgerTransactionId={}, type={}, entries={}",
                transaction.getId(), transaction.getType(), savedEntries.size());
        return toResponse(transaction, savedEntries);
    }

    private NormalizedDraft normalizeDraft(LedgerEntryDraft draft) {
        if (draft == null || draft.ledgerAccountId() == null || draft.direction() == null) {
            throw new InvalidRequestException("Ledger entry requires ledgerAccountId, direction and amount");
        }
        BigDecimal amount = requirePositiveAmount(draft.amount());
        return new NormalizedDraft(draft.ledgerAccountId(), draft.direction(), amount);
    }

    private static BigDecimal requirePositiveAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidRequestException("Ledger amount must be greater than zero");
        }
        try {
            return amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw new InvalidRequestException("Ledger amount must have at most 2 decimal places");
        }
    }

    private static String requireIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new InvalidRequestException("idempotencyKey is required");
        }
        String trimmed = idempotencyKey.trim();
        if (trimmed.length() > 120) {
            throw new InvalidRequestException("idempotencyKey exceeds 120 characters");
        }
        return trimmed;
    }

    private LedgerAccount customerLedger(UUID accountId) {
        return ledgerAccountRepository.findByAccount_Id(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("LedgerAccount", "accountId", accountId));
    }

    private LedgerAccount ensureClearing(UUID organizationId, String currency) {
        return ledgerAccountRepository
                .findByOrganization_IdAndKindAndCurrency(organizationId, LedgerAccountKind.CLEARING, currency)
                .orElseGet(() -> createClearing(organizationId, currency));
    }

    private LedgerAccount createClearing(UUID organizationId, String currency) {
        try {
            Organization organization = organizationRepository.findById(organizationId)
                    .orElseThrow(() -> new ResourceNotFoundException("Organization", "id", organizationId));
            return ledgerAccountRepository.save(LedgerAccount.builder()
                    .organization(organization)
                    .kind(LedgerAccountKind.CLEARING)
                    .currency(currency)
                    .build());
        } catch (DataIntegrityViolationException ex) {
            return ledgerAccountRepository
                    .findByOrganization_IdAndKindAndCurrency(organizationId, LedgerAccountKind.CLEARING, currency)
                    .orElseThrow(() -> ex);
        }
    }

    private static BigDecimal sum(List<NormalizedDraft> drafts, LedgerDirection direction) {
        return drafts.stream()
                .filter(d -> d.direction() == direction)
                .map(NormalizedDraft::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal normalizeZero(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2);
        }
        try {
            return value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            return value.stripTrailingZeros().setScale(2, RoundingMode.UNNECESSARY);
        }
    }

    private LedgerTransactionResponse toResponse(LedgerTransaction transaction) {
        return toResponse(transaction, ledgerEntryRepository.findByTransactionIdWithAccount(transaction.getId()));
    }

    private static LedgerTransactionResponse toResponse(
            LedgerTransaction transaction, List<LedgerEntry> entries) {
        return LedgerTransactionResponse.builder()
                .id(transaction.getId())
                .reference(transaction.getReference())
                .type(transaction.getType())
                .status(transaction.getStatus())
                .idempotencyKey(transaction.getIdempotencyKey())
                .createdAt(transaction.getCreatedAt())
                .entries(entries.stream()
                        .map(e -> LedgerTransactionResponse.Entry.builder()
                                .id(e.getId())
                                .ledgerAccountId(e.getLedgerAccount().getId())
                                .direction(e.getDirection())
                                .amount(e.getAmount())
                                .build())
                        .toList())
                .build();
    }

    private record NormalizedDraft(UUID ledgerAccountId, LedgerDirection direction, BigDecimal amount) {
    }
}
