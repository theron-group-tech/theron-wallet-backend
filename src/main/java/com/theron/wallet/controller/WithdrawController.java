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
    @Operation(summary = "Create a withdrawal",
            description = "Debits the wallet and creates an Asaas transfer to the specified PIX key. "
                    + "Returns PENDING until confirmed by Asaas webhook.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Withdrawal created, transfer pending"),
            @ApiResponse(responseCode = "400", description = "Validation error"),
            @ApiResponse(responseCode = "403", description = "Subaccount blocked for outbound operations"),
            @ApiResponse(responseCode = "404", description = "Customer or wallet not found"),
            @ApiResponse(responseCode = "409", description = "Insufficient balance")
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
