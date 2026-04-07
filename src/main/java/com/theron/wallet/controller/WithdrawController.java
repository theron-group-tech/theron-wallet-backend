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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
