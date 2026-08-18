package com.theron.wallet.controller;

import com.theron.wallet.dto.response.TransactionResponse;
import com.theron.wallet.enums.TransactionType;
import com.theron.wallet.exception.InvalidRequestException;
import com.theron.wallet.security.ActorResolver;
import com.theron.wallet.security.PermissionCodes;
import com.theron.wallet.security.ResourceAuthorization;
import com.theron.wallet.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
@Tag(name = "Transactions", description = "Transaction history scoped to a wallet or subaccount the caller can read")
public class TransactionController {

    private final TransactionService transactionService;
    private final ActorResolver actorResolver;
    private final ResourceAuthorization resourceAuthorization;

    @GetMapping
    @Operation(summary = "Listar transações", description = "walletId or subaccountId is required. Unscoped listing is forbidden.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista retornada com sucesso")
    })
    public ResponseEntity<Page<TransactionResponse>> findAll(
            @RequestParam(required = false) UUID walletId,
            @RequestParam(required = false) UUID subaccountId,
            @RequestParam(required = false) TransactionType type,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        UUID actor = actorResolver.requireProductUserId();
        if (walletId != null) {
            resourceAuthorization.requireWallet(actor, walletId, PermissionCodes.TRANSACTIONS_READ);
        } else if (subaccountId != null) {
            resourceAuthorization.requireSubaccount(actor, subaccountId, PermissionCodes.TRANSACTIONS_READ);
        } else {
            throw new InvalidRequestException("walletId or subaccountId is required");
        }
        return ResponseEntity.ok(transactionService.findAll(walletId, subaccountId, type, pageable));
    }

    @GetMapping("/{transactionId}")
    @Operation(summary = "Buscar transação por ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Transação encontrada"),
            @ApiResponse(responseCode = "404", description = "Transação não encontrada")
    })
    public ResponseEntity<TransactionResponse> findById(@PathVariable UUID transactionId) {
        resourceAuthorization.requireTransaction(
                actorResolver.requireProductUserId(), transactionId, PermissionCodes.TRANSACTIONS_READ);
        return ResponseEntity.ok(transactionService.findById(transactionId));
    }
}
