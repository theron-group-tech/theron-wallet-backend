package com.theron.wallet.controller;

import com.theron.wallet.dto.request.WithdrawRequest;
import com.theron.wallet.dto.response.WithdrawResponse;
import com.theron.wallet.service.WithdrawService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/withdraws")
@RequiredArgsConstructor
@Tag(name = "Withdrawals", description = "Cash-out operations via Asaas transfers")
public class WithdrawController {

    private final WithdrawService withdrawService;

    @PostMapping
    @Operation(summary = "Criar saque via Pix",
            description = "Debita a carteira da subconta e cria uma transferência Pix no Asaas. Retorna PENDING até confirmação via webhook.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Saque criado, transferência pendente"),
            @ApiResponse(responseCode = "400", description = "Erro de validação"),
            @ApiResponse(responseCode = "404", description = "Subconta ou carteira não encontrada"),
            @ApiResponse(responseCode = "409", description = "Saldo insuficiente"),
            @ApiResponse(responseCode = "422", description = "Subconta não elegível para saques (status inválido)")
    })
    public ResponseEntity<WithdrawResponse> createWithdraw(@Valid @RequestBody WithdrawRequest request) {
        WithdrawResponse response = withdrawService.createWithdraw(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "Listar saques",
            description = "Lista saques paginados. Forneça `walletId` ou `subaccountId` como filtro obrigatório. "
                    + "Exemplo: `GET /api/v1/withdraws?subaccountId=xxx&page=0&size=10&sort=createdAt,desc`")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista retornada com sucesso"),
            @ApiResponse(responseCode = "400", description = "walletId ou subaccountId obrigatório")
    })
    public ResponseEntity<Page<WithdrawResponse>> findAll(
            @RequestParam(required = false) UUID walletId,
            @RequestParam(required = false) UUID subaccountId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(withdrawService.findAll(walletId, subaccountId, pageable));
    }

    @GetMapping("/{transactionId}")
    @Operation(summary = "Get withdrawal by transaction ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Withdrawal found"),
            @ApiResponse(responseCode = "404", description = "Transaction not found")
    })
    public ResponseEntity<WithdrawResponse> findById(@PathVariable UUID transactionId) {
        return ResponseEntity.ok(withdrawService.findById(transactionId));
    }
}
