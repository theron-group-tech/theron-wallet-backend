package com.theron.wallet.service.impl;

import com.theron.wallet.dto.response.AsaasBindResponse;
import com.theron.wallet.dto.asaas.AsaasFinancialTransactionResponse;
import com.theron.wallet.dto.asaas.AsaasListResponse;
import com.theron.wallet.dto.response.AccountResponse;
import com.theron.wallet.dto.response.DashboardResponse;
import com.theron.wallet.dto.response.MeResponse;
import com.theron.wallet.dto.response.NotificationResponse;
import com.theron.wallet.dto.response.PageResponse;
import com.theron.wallet.dto.response.TransactionResponse;
import com.theron.wallet.dto.response.WalletResponse;
import com.theron.wallet.entity.Account;
import com.theron.wallet.entity.Subaccount;
import com.theron.wallet.entity.User;
import com.theron.wallet.enums.SubaccountStatus;
import com.theron.wallet.enums.TransactionStatus;
import com.theron.wallet.enums.TransactionType;
import com.theron.wallet.exception.ResourceNotFoundException;
import com.theron.wallet.mapper.AccountMapper;
import com.theron.wallet.mapper.TransactionMapper;
import com.theron.wallet.repository.AccountRepository;
import com.theron.wallet.repository.SubaccountRepository;
import com.theron.wallet.repository.TransactionRepository;
import com.theron.wallet.repository.UserRepository;
import com.theron.wallet.repository.WalletRepository;
import com.theron.wallet.security.AsaasApiKeyResolver;
import com.theron.wallet.service.AccountAsaasProvisioningService;
import com.theron.wallet.service.AsaasBalanceService;
import com.theron.wallet.service.AsaasOnboardingService;
import com.theron.wallet.service.MeService;
import com.theron.wallet.service.MobileScopeService;
import com.theron.wallet.service.NotificationService;
import com.theron.wallet.service.StatementService;
import com.theron.wallet.service.UserService;
import com.theron.wallet.integration.AsaasFinancialTransactionClient;
import com.theron.wallet.web.MobilePageables;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MeServiceImpl implements MeService {

    static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final String CURRENCY = "BRL";
    private static final int ASAAS_STATEMENT_LIMIT = 100;
    private static final int RECENT_TRANSACTION_LIMIT = 4;
    private static final List<TransactionStatus> PENDING_STATUSES = List.of(
            TransactionStatus.PENDING, TransactionStatus.PENDING_APPROVAL, TransactionStatus.PROCESSING);

    private final UserRepository userRepository;
    private final UserService userService;
    private final MobileScopeService mobileScopeService;
    private final AccountRepository accountRepository;
    private final WalletRepository walletRepository;
    private final SubaccountRepository subaccountRepository;
    private final TransactionRepository transactionRepository;
    private final StatementService statementService;
    private final NotificationService notificationService;
    private final AccountAsaasProvisioningService accountAsaasProvisioningService;
    private final AsaasBalanceService asaasBalanceService;
    private final AsaasOnboardingService asaasOnboardingService;
    private final AsaasApiKeyResolver asaasApiKeyResolver;
    private final AsaasFinancialTransactionClient asaasFinancialTransactionClient;

    @Override
    @Transactional(readOnly = true)
    public MeResponse me(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        return MeResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .status(user.getStatus())
                .lastLoginAt(user.getLastLoginAt())
                .organizations(userService.listOrganizations(userId))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public DashboardResponse dashboard(UUID userId, UUID organizationId, UUID accountId) {
        List<UUID> accountIds = mobileScopeService.accountIds(userId, organizationId, accountId);
        BigDecimal ledgerBalance = money(accountIds.isEmpty()
                ? BigDecimal.ZERO
                : walletRepository.sumBalanceByAccountIds(accountIds));
        BigDecimal balance = accountIds.isEmpty()
                ? money(BigDecimal.ZERO)
                : asaasBalanceService.sumDisplayBalances(accountIds);
        BigDecimal blocked = money(accountIds.isEmpty()
                ? BigDecimal.ZERO
                : transactionRepository.sumByAccountIdsAndTypeAndStatus(
                        accountIds, TransactionType.PIX, TransactionStatus.PENDING_APPROVAL));
        BigDecimal available = balance.subtract(blocked).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);

        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        List<AsaasFinancialTransactionResponse> todayStatement = new ArrayList<>();
        List<AsaasFinancialTransactionResponse> recentStatement = new ArrayList<>();

        for (UUID currentAccountId : accountIds) {
            todayStatement.addAll(fetchAsaasStatement(currentAccountId, today, today));
            recentStatement.addAll(fetchAsaasStatement(currentAccountId, today.minusDays(30), today));
        }

        BigDecimal income = money(todayStatement.stream()
                .map(AsaasFinancialTransactionResponse::getValue)
                .filter(value -> value != null && value.signum() > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal expenses = money(todayStatement.stream()
                .map(AsaasFinancialTransactionResponse::getValue)
                .filter(value -> value != null && value.signum() < 0)
                .map(BigDecimal::abs)
                .reduce(BigDecimal.ZERO, BigDecimal::add));

        List<TransactionResponse> pending = accountIds.isEmpty()
                ? List.of()
                : transactionRepository.findByAccount_IdInAndStatusIn(
                                accountIds, PENDING_STATUSES, PageRequest.of(0, 10, MobilePageables.DEFAULT_SORT))
                        .map(TransactionMapper::toMobileResponse)
                        .getContent();

        List<TransactionResponse> recent = recentStatement.stream()
                .sorted(Comparator.comparing(
                        AsaasFinancialTransactionResponse::getDate,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(RECENT_TRANSACTION_LIMIT)
                .map(transaction -> toMobileAsaasTransaction(transaction, accountIds))
                .toList();

        return DashboardResponse.builder()
                .balance(balance)
                .ledgerBalance(ledgerBalance)
                .availableBalance(available)
                .blockedBalance(blocked)
                .currency(CURRENCY)
                .todayIncome(income)
                .todayExpenses(expenses)
                .pendingTransactions(pending)
                .recentTransactions(recent)
                .organizationId(organizationId)
                .accountId(accountId)
                .build();
    }

    private List<AsaasFinancialTransactionResponse> fetchAsaasStatement(
            UUID accountId, LocalDate startDate, LocalDate finishDate) {
        Subaccount subaccount = subaccountRepository.findByAccount_Id(accountId).orElse(null);
        if (subaccount == null
                || subaccount.getStatus() != SubaccountStatus.ACTIVE
                || subaccount.getEncryptedApiKey() == null) {
            return List.of();
        }

        try {
            String apiKey = asaasApiKeyResolver.resolveForSubaccount(subaccount.getId());
            AsaasListResponse<AsaasFinancialTransactionResponse> response = asaasFinancialTransactionClient.list(
                    apiKey,
                    startDate.toString(),
                    finishDate.toString(),
                    0,
                    ASAAS_STATEMENT_LIMIT);
            if (response == null || response.getData() == null) {
                return List.of();
            }
            return response.getData();
        } catch (Exception ex) {
            log.warn(
                    "Unable to fetch Asaas financial statement for accountId={}, period={}..{}: {}",
                    accountId,
                    startDate,
                    finishDate,
                    ex.getMessage());
            return List.of();
        }
    }

    private TransactionResponse toMobileAsaasTransaction(
            AsaasFinancialTransactionResponse transaction,
            List<UUID> accountIds) {
        BigDecimal value = money(transaction.getValue());
        TransactionType type = mapAsaasTransactionType(transaction.getType(), value);
        LocalDate date = transaction.getDate();
        LocalDateTime timestamp = date != null ? date.atStartOfDay() : null;
        String reference = transaction.getId();
        String description = transaction.getDescription();
        if (description == null || description.isBlank()) {
            description = describeAsaasType(transaction.getType());
        }

        return TransactionResponse.builder()
                .id(null)
                .walletId(null)
                .organizationId(null)
                .accountId(accountIds.size() == 1 ? accountIds.get(0) : null)
                .type(type)
                .status(TransactionStatus.COMPLETED)
                .amount(value.abs())
                .currency(CURRENCY)
                .reference(reference)
                .description(description)
                .counterpartHint(null)
                .asaasPaymentId(transaction.getPaymentId())
                .createdAt(timestamp)
                .updatedAt(timestamp)
                .completedAt(timestamp)
                .build();
    }

    private static TransactionType mapAsaasTransactionType(String asaasType, BigDecimal value) {
        String type = asaasType == null ? "" : asaasType.toUpperCase();

        if (type.contains("FEE")) {
            return TransactionType.FEE;
        }
        if (type.contains("REFUND") || type.contains("REVERSAL")) {
            return value.signum() > 0 ? TransactionType.REFUND : TransactionType.WITHDRAWAL;
        }
        if (type.contains("TRANSFER") && value.signum() > 0) {
            return TransactionType.TRANSFER_IN;
        }
        if (type.contains("TRANSFER") && value.signum() < 0) {
            return TransactionType.TRANSFER_OUT;
        }
        if (value.signum() > 0) {
            return TransactionType.DEPOSIT;
        }
        return TransactionType.WITHDRAWAL;
    }

    private static String describeAsaasType(String asaasType) {
        if (asaasType == null || asaasType.isBlank()) {
            return "Movimentação financeira";
        }

        return switch (asaasType.toUpperCase()) {
            case "PAYMENT_RECEIVED" -> "Cobrança recebida";
            case "PIX_TRANSACTION_CREDIT" -> "PIX recebido";
            case "TRANSFER" -> "Transferência";
            case "TRANSFER_FEE" -> "Tarifa de transferência";
            case "PAYMENT_FEE" -> "Tarifa de cobrança";
            case "PAYMENT_REVERSAL", "REVERSAL" -> "Estorno";
            case "RECEIVABLE_ANTICIPATION_GROSS_CREDIT" -> "Antecipação de recebíveis";
            case "RECEIVABLE_ANTICIPATION_FEE" -> "Tarifa de antecipação";
            default -> asaasType.replace('_', ' ');
        };
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AccountResponse> accounts(UUID userId, UUID organizationId, Pageable pageable) {
        Pageable clamped = MobilePageables.clamp(pageable);
        List<Account> ownedAccounts = mobileScopeService.accounts(userId, organizationId, null);
        if (ownedAccounts.isEmpty()) {
            return PageResponse.from(Page.empty(clamped));
        }

        int start = (int) Math.min(clamped.getOffset(), ownedAccounts.size());
        int end = Math.min(start + clamped.getPageSize(), ownedAccounts.size());
        List<Account> pageContent = ownedAccounts.subList(start, end);
        Page<Account> page = new PageImpl<>(pageContent, clamped, ownedAccounts.size());
        return PageResponse.from(page.map(this::toEnrichedAccountResponse));
    }

    private AccountResponse toEnrichedAccountResponse(Account account) {
        AsaasBindResponse bind = accountAsaasProvisioningService.currentBind(account.getId());
        AccountResponse response = AccountMapper.toResponse(account, bind);
        asaasOnboardingService.enrichAccountResponse(response, account.getId());
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<WalletResponse> wallets(
            UUID userId, UUID organizationId, UUID accountId, Pageable pageable) {
        Pageable clamped = MobilePageables.clamp(pageable);
        List<UUID> accountIds = mobileScopeService.accountIds(userId, organizationId, accountId);
        if (accountIds.isEmpty()) {
            return PageResponse.from(Page.empty(clamped));
        }
        return PageResponse.from(walletRepository.findByAccount_IdIn(accountIds, clamped)
                .map(this::toWalletResponse));
    }

    private WalletResponse toWalletResponse(com.theron.wallet.entity.Wallet wallet) {
        UUID accountId = wallet.getAccount() != null ? wallet.getAccount().getId() : null;
        BigDecimal ledger = money(wallet.getBalance());
        var asaas = accountId != null ? asaasBalanceService.fetchAsaasBalance(accountId) : java.util.Optional.<BigDecimal>empty();
        return WalletResponse.builder()
                .id(wallet.getId())
                .subaccountId(wallet.getSubaccount() != null ? wallet.getSubaccount().getId() : null)
                .accountId(accountId)
                .balance(asaas.orElse(ledger))
                .ledgerBalance(ledger)
                .asaasBalanceUnavailable(accountId != null && asaas.isEmpty()
                        && wallet.getSubaccount() != null
                        && wallet.getSubaccount().getStatus() == com.theron.wallet.enums.SubaccountStatus.ACTIVE)
                .currency(wallet.getCurrency())
                .active(wallet.getActive())
                .createdAt(wallet.getCreatedAt())
                .updatedAt(wallet.getUpdatedAt())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> transactions(
            UUID userId,
            UUID organizationId,
            UUID accountId,
            LocalDateTime from,
            LocalDateTime to,
            TransactionType type,
            TransactionStatus status,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            Pageable pageable) {
        List<UUID> accountIds = mobileScopeService.accountIds(userId, organizationId, accountId);
        return statementService.list(
                accountIds, from, to, type, status, minAmount, maxAmount, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> notifications(
            UUID userId, UUID organizationId, boolean unreadOnly, Pageable pageable) {
        Pageable clamped = MobilePageables.clamp(pageable);
        return PageResponse.from(notificationService.list(userId, organizationId, unreadOnly, clamped));
    }

    private static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }
}
