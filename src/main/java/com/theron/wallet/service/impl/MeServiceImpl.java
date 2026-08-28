package com.theron.wallet.service.impl;

import com.theron.wallet.dto.response.AccountResponse;
import com.theron.wallet.dto.response.DashboardResponse;
import com.theron.wallet.dto.response.MeResponse;
import com.theron.wallet.dto.response.NotificationResponse;
import com.theron.wallet.dto.response.PageResponse;
import com.theron.wallet.dto.response.TransactionResponse;
import com.theron.wallet.dto.response.WalletResponse;
import com.theron.wallet.entity.User;
import com.theron.wallet.enums.TransactionStatus;
import com.theron.wallet.enums.TransactionType;
import com.theron.wallet.exception.ResourceNotFoundException;
import com.theron.wallet.dto.response.AsaasBindResponse;
import com.theron.wallet.entity.Account;
import com.theron.wallet.mapper.AccountMapper;
import com.theron.wallet.mapper.TransactionMapper;
import com.theron.wallet.repository.AccountRepository;
import com.theron.wallet.repository.TransactionRepository;
import com.theron.wallet.repository.UserRepository;
import com.theron.wallet.repository.WalletRepository;
import com.theron.wallet.service.AccountAsaasProvisioningService;
import com.theron.wallet.service.AsaasBalanceService;
import com.theron.wallet.service.MeService;
import com.theron.wallet.service.MobileScopeService;
import com.theron.wallet.service.NotificationService;
import com.theron.wallet.service.StatementService;
import com.theron.wallet.service.UserService;
import com.theron.wallet.web.MobilePageables;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MeServiceImpl implements MeService {

    static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final String CURRENCY = "BRL";
    private static final Set<TransactionType> INCOME_TYPES = Set.of(
            TransactionType.DEPOSIT, TransactionType.TRANSFER_IN, TransactionType.REFUND);
    private static final Set<TransactionType> EXPENSE_TYPES = Set.of(
            TransactionType.WITHDRAWAL, TransactionType.TRANSFER_OUT, TransactionType.PIX,
            TransactionType.PAYMENT, TransactionType.FEE);
    private static final List<TransactionStatus> PENDING_STATUSES = List.of(
            TransactionStatus.PENDING, TransactionStatus.PENDING_APPROVAL, TransactionStatus.PROCESSING);

    private final UserRepository userRepository;
    private final UserService userService;
    private final MobileScopeService mobileScopeService;
    private final AccountRepository accountRepository;
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final StatementService statementService;
    private final NotificationService notificationService;
    private final AccountAsaasProvisioningService accountAsaasProvisioningService;
    private final AsaasBalanceService asaasBalanceService;

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
        LocalDateTime dayStart = today.atStartOfDay();
        LocalDateTime dayEnd = today.plusDays(1).atStartOfDay();
        BigDecimal income = money(accountIds.isEmpty()
                ? BigDecimal.ZERO
                : transactionRepository.sumCompletedByTypesAndCompletedAtBetween(
                        accountIds, INCOME_TYPES, dayStart, dayEnd));
        BigDecimal expenses = money(accountIds.isEmpty()
                ? BigDecimal.ZERO
                : transactionRepository.sumCompletedByTypesAndCompletedAtBetween(
                        accountIds, EXPENSE_TYPES, dayStart, dayEnd));

        List<TransactionResponse> pending = accountIds.isEmpty()
                ? List.of()
                : transactionRepository.findByAccount_IdInAndStatusIn(
                                accountIds, PENDING_STATUSES, PageRequest.of(0, 10, MobilePageables.DEFAULT_SORT))
                        .map(TransactionMapper::toMobileResponse)
                        .getContent();
        List<TransactionResponse> recent = accountIds.isEmpty()
                ? List.of()
                : transactionRepository.findByAccount_IdIn(
                                accountIds, PageRequest.of(0, 4, MobilePageables.DEFAULT_SORT))
                        .map(TransactionMapper::toMobileResponse)
                        .getContent();

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

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AccountResponse> accounts(UUID userId, UUID organizationId, Pageable pageable) {
        Pageable clamped = MobilePageables.clamp(pageable);
        List<UUID> orgIds = mobileScopeService.organizationIds(userId, organizationId);
        if (orgIds.isEmpty()) {
            return PageResponse.from(Page.empty(clamped));
        }
        return PageResponse.from(accountRepository.findByOrganization_IdIn(orgIds, clamped)
                .map(this::toEnrichedAccountResponse));
    }

    private AccountResponse toEnrichedAccountResponse(Account account) {
        AsaasBindResponse bind = accountAsaasProvisioningService.currentBind(account.getId());
        return AccountMapper.toResponse(account, bind);
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
