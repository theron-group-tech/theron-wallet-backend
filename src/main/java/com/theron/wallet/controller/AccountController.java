package com.theron.wallet.controller;

import com.theron.wallet.dto.request.UpdateAccountRequest;
import com.theron.wallet.dto.response.AccountResponse;
import com.theron.wallet.dto.response.AsaasBindResponse;
import com.theron.wallet.dto.response.LedgerBalanceResponse;
import com.theron.wallet.dto.response.PageResponse;
import com.theron.wallet.dto.response.TransactionResponse;
import com.theron.wallet.dto.response.WalletResponse;
import com.theron.wallet.enums.TransactionStatus;
import com.theron.wallet.enums.TransactionType;
import com.theron.wallet.security.ActorResolver;
import com.theron.wallet.security.PermissionCodes;
import com.theron.wallet.security.ResourceAuthorization;
import com.theron.wallet.service.AccountAsaasProvisioningService;
import com.theron.wallet.service.AccountService;
import com.theron.wallet.service.LedgerService;
import com.theron.wallet.service.StatementService;
import com.theron.wallet.web.MobilePageables;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
@Tag(name = "Accounts", description = "Financial account by id. Does not expose Asaas credentials.")
public class AccountController {

    private final AccountService accountService;
    private final AccountAsaasProvisioningService accountAsaasProvisioningService;
    private final LedgerService ledgerService;
    private final StatementService statementService;
    private final ActorResolver actorResolver;
    private final ResourceAuthorization resourceAuthorization;

    @GetMapping("/{id}")
    @Operation(summary = "Get account by id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Account found"),
            @ApiResponse(responseCode = "404", description = "Account not found")
    })
    public ResponseEntity<AccountResponse> findById(@PathVariable UUID id) {
        resourceAuthorization.requireAccount(actorResolver.requireProductUserId(), id, PermissionCodes.WALLET_READ);
        return ResponseEntity.ok(accountService.findById(id));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update account", description = "Updates name and/or status. type, currency and organization are immutable.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Account updated"),
            @ApiResponse(responseCode = "404", description = "Account not found"),
            @ApiResponse(responseCode = "422", description = "Invalid data")
    })
    public ResponseEntity<AccountResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateAccountRequest request) {
        resourceAuthorization.requireAccount(
                actorResolver.requireProductUserId(), id, PermissionCodes.ORGANIZATION_UPDATE);
        return ResponseEntity.ok(accountService.update(id, request));
    }

    @GetMapping("/{id}/wallet")
    @Operation(summary = "Get wallet owned by the account")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Wallet found"),
            @ApiResponse(responseCode = "404", description = "Account or wallet not found")
    })
    public ResponseEntity<WalletResponse> findWallet(@PathVariable UUID id) {
        resourceAuthorization.requireAccount(actorResolver.requireProductUserId(), id, PermissionCodes.WALLET_READ);
        return ResponseEntity.ok(accountService.findWallet(id));
    }

    @PostMapping("/{accountId}/asaas-subaccount")
    @Operation(summary = "Provision or retry Asaas subaccount bind for the caller's own account")
    public ResponseEntity<AsaasBindResponse> provisionAsaas(@PathVariable UUID accountId) {
        UUID actor = actorResolver.requireProductUserId();
        // Own-account isolation: any product role may provision/repair their own Account bind.
        resourceAuthorization.requireAccount(actor, accountId, PermissionCodes.WALLET_READ);
        return ResponseEntity.ok(accountAsaasProvisioningService.provisionByAccountId(accountId, null));
    }

    @GetMapping("/{id}/ledger-balance")
    @Operation(summary = "Get reconstructed ledger balance for the account")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Balance reconstructed from ledger entries"),
            @ApiResponse(responseCode = "404", description = "Account not found")
    })
    public ResponseEntity<LedgerBalanceResponse> ledgerBalance(@PathVariable UUID id) {
        resourceAuthorization.requireAccount(actorResolver.requireProductUserId(), id, PermissionCodes.WALLET_READ);
        return ResponseEntity.ok(ledgerService.getBalance(id));
    }

    @GetMapping("/{accountId}/statement")
    @Operation(summary = "Account statement for the authenticated product user")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Statement page"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid access token"),
            @ApiResponse(responseCode = "403", description = "Account belongs to another organization"),
            @ApiResponse(responseCode = "404", description = "Account not found")
    })
    public ResponseEntity<PageResponse<TransactionResponse>> statement(
            @PathVariable UUID accountId,
            @RequestParam(required = false) LocalDateTime from,
            @RequestParam(required = false) LocalDateTime to,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) TransactionStatus status,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            @PageableDefault(size = MobilePageables.DEFAULT_SIZE, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(statementService.statement(
                actorResolver.requireProductUserId(),
                accountId,
                from,
                to,
                type,
                status,
                minAmount,
                maxAmount,
                pageable));
    }
}
