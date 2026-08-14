package com.theron.wallet.controller;

import com.theron.wallet.dto.request.UpdateAccountRequest;
import com.theron.wallet.dto.response.AccountResponse;
import com.theron.wallet.dto.response.LedgerBalanceResponse;
import com.theron.wallet.dto.response.WalletResponse;
import com.theron.wallet.service.AccountService;
import com.theron.wallet.service.LedgerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
@Tag(name = "Accounts", description = "Financial account by id. Does not expose Asaas credentials.")
public class AccountController {

    private final AccountService accountService;
    private final LedgerService ledgerService;

    @GetMapping("/{id}")
    @Operation(summary = "Get account by id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Account found"),
            @ApiResponse(responseCode = "404", description = "Account not found")
    })
    public ResponseEntity<AccountResponse> findById(@PathVariable UUID id) {
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
        return ResponseEntity.ok(accountService.update(id, request));
    }

    @GetMapping("/{id}/wallet")
    @Operation(summary = "Get wallet owned by the account")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Wallet found"),
            @ApiResponse(responseCode = "404", description = "Account or wallet not found")
    })
    public ResponseEntity<WalletResponse> findWallet(@PathVariable UUID id) {
        return ResponseEntity.ok(accountService.findWallet(id));
    }

    @GetMapping("/{id}/ledger-balance")
    @Operation(summary = "Get reconstructed ledger balance for the account")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Balance reconstructed from ledger entries"),
            @ApiResponse(responseCode = "404", description = "Account not found")
    })
    public ResponseEntity<LedgerBalanceResponse> ledgerBalance(@PathVariable UUID id) {
        return ResponseEntity.ok(ledgerService.getBalance(id));
    }
}
