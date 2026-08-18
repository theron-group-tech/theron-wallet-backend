package com.theron.wallet.controller;

import com.theron.wallet.dto.response.AccountResponse;
import com.theron.wallet.dto.response.DashboardResponse;
import com.theron.wallet.dto.response.MeResponse;
import com.theron.wallet.dto.response.NotificationResponse;
import com.theron.wallet.dto.response.PageResponse;
import com.theron.wallet.dto.response.TransactionResponse;
import com.theron.wallet.dto.response.WalletResponse;
import com.theron.wallet.enums.TransactionStatus;
import com.theron.wallet.enums.TransactionType;
import com.theron.wallet.security.ActorResolver;
import com.theron.wallet.service.MeService;
import com.theron.wallet.web.MobilePageables;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
@Tag(name = "Mobile Me", description = "Product-user mobile API. Requires JWT access token.")
public class MeController {

    private final MeService meService;
    private final ActorResolver actorResolver;

    @GetMapping
    @Operation(summary = "Current user profile and organizations")
    public ResponseEntity<MeResponse> me() {
        return ResponseEntity.ok(meService.me(actorResolver.requireProductUserId(null)));
    }

    @GetMapping("/dashboard")
    @Operation(summary = "Aggregated dashboard for the caller's accounts")
    public ResponseEntity<DashboardResponse> dashboard(
            @RequestParam(required = false) UUID organizationId,
            @RequestParam(required = false) UUID accountId) {
        return ResponseEntity.ok(meService.dashboard(
                actorResolver.requireProductUserId(null), organizationId, accountId));
    }

    @GetMapping("/accounts")
    @Operation(summary = "Accounts visible to the caller")
    public ResponseEntity<PageResponse<AccountResponse>> accounts(
            @RequestParam(required = false) UUID organizationId,
            @PageableDefault(size = MobilePageables.DEFAULT_SIZE, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(meService.accounts(
                actorResolver.requireProductUserId(null), organizationId, pageable));
    }

    @GetMapping("/wallets")
    @Operation(summary = "Wallets visible to the caller")
    public ResponseEntity<PageResponse<WalletResponse>> wallets(
            @RequestParam(required = false) UUID organizationId,
            @RequestParam(required = false) UUID accountId,
            @PageableDefault(size = MobilePageables.DEFAULT_SIZE, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(meService.wallets(
                actorResolver.requireProductUserId(null), organizationId, accountId, pageable));
    }

    @GetMapping("/transactions")
    @Operation(summary = "Transactions across the caller's accounts")
    public ResponseEntity<PageResponse<TransactionResponse>> transactions(
            @RequestParam(required = false) UUID organizationId,
            @RequestParam(required = false) UUID accountId,
            @RequestParam(required = false) LocalDateTime from,
            @RequestParam(required = false) LocalDateTime to,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) TransactionStatus status,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            @PageableDefault(size = MobilePageables.DEFAULT_SIZE, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(meService.transactions(
                actorResolver.requireProductUserId(null),
                organizationId,
                accountId,
                from,
                to,
                type,
                status,
                minAmount,
                maxAmount,
                pageable));
    }

    @GetMapping("/notifications")
    @Operation(summary = "Inbox for the caller")
    public ResponseEntity<PageResponse<NotificationResponse>> notifications(
            @RequestParam(required = false) UUID organizationId,
            @RequestParam(required = false, defaultValue = "false") boolean unreadOnly,
            @PageableDefault(size = MobilePageables.DEFAULT_SIZE, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(meService.notifications(
                actorResolver.requireProductUserId(null), organizationId, unreadOnly, pageable));
    }
}
