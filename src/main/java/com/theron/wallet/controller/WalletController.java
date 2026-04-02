package com.theron.wallet.controller;

import com.theron.wallet.dto.response.TransactionResponse;
import com.theron.wallet.dto.response.WalletResponse;
import com.theron.wallet.enums.TransactionStatus;
import com.theron.wallet.enums.TransactionType;
import com.theron.wallet.service.TransactionService;
import com.theron.wallet.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/wallets")
@RequiredArgsConstructor
@Tag(name = "Wallets", description = "Wallet management and transaction history")
public class WalletController {

    private final WalletService walletService;
    private final TransactionService transactionService;

    @GetMapping("/customer/{customerId}")
    @Operation(summary = "Get wallet by customer ID", description = "Returns the wallet for a given customer")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Wallet found"),
            @ApiResponse(responseCode = "404", description = "Wallet not found for customer")
    })
    public ResponseEntity<WalletResponse> findByCustomerId(@PathVariable UUID customerId) {
        return ResponseEntity.ok(walletService.findByCustomerId(customerId));
    }

    @GetMapping("/{walletId}")
    @Operation(summary = "Get wallet by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Wallet found"),
            @ApiResponse(responseCode = "404", description = "Wallet not found")
    })
    public ResponseEntity<WalletResponse> findById(@PathVariable UUID walletId) {
        return ResponseEntity.ok(walletService.findById(walletId));
    }

    @GetMapping("/{walletId}/transactions")
    @Operation(summary = "List transactions for a wallet", description = "Paginated list with optional type/status filter")
    public ResponseEntity<Page<TransactionResponse>> findTransactions(
            @PathVariable UUID walletId,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) TransactionStatus status,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {

        Page<TransactionResponse> page;
        if (type != null) {
            page = transactionService.findByWalletIdAndType(walletId, type, pageable);
        } else if (status != null) {
            page = transactionService.findByWalletIdAndStatus(walletId, status, pageable);
        } else {
            page = transactionService.findByWalletId(walletId, pageable);
        }

        return ResponseEntity.ok(page);
    }
}
