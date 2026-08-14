package com.theron.wallet.controller;

import com.theron.wallet.dto.response.TransactionResponse;
import com.theron.wallet.enums.TransactionType;
import com.theron.wallet.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
@Tag(name = "Transactions", description = "Global transaction history — search across all wallets and subaccounts")
public class TransactionController {

    private final TransactionService transactionService;

    @GetMapping
    @Operation(
            summary = "Listar transações (global)",
            description = """
                    Lista transações com filtros opcionais e paginação.
                    
                    Combinações suportadas:
                    - Sem filtros → todas as transações (ordenadas por `createdAt DESC`)
                    - `?walletId=xxx` → transações de uma carteira específica
                    - `?subaccountId=xxx` → transações de todas as carteiras de uma subconta
                    - `?walletId=xxx&type=DEPOSIT` → depósitos de uma carteira
                    - `?subaccountId=xxx&type=WITHDRAWAL` → saques de uma subconta
                    
                    Tipos disponíveis: `DEPOSIT`, `WITHDRAWAL`, `TRANSFER_IN`, `TRANSFER_OUT`,
                    `TRANSFER`, `PIX`, `PAYMENT`, `REFUND`, `FEE`
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista retornada com sucesso")
    })
    public ResponseEntity<Page<TransactionResponse>> findAll(
            @RequestParam(required = false) UUID walletId,
            @RequestParam(required = false) UUID subaccountId,
            @RequestParam(required = false) TransactionType type,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        log.info("GET /api/v1/transactions — walletId={}, subaccountId={}, type={}", walletId, subaccountId, type);
        return ResponseEntity.ok(transactionService.findAll(walletId, subaccountId, type, pageable));
    }

    @GetMapping("/{transactionId}")
    @Operation(summary = "Buscar transação por ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Transação encontrada"),
            @ApiResponse(responseCode = "404", description = "Transação não encontrada")
    })
    public ResponseEntity<TransactionResponse> findById(@PathVariable UUID transactionId) {
        log.info("GET /api/v1/transactions/{}", transactionId);
        return ResponseEntity.ok(transactionService.findById(transactionId));
    }
}

