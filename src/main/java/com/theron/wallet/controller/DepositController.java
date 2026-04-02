package com.theron.wallet.controller;

import com.theron.wallet.dto.request.DepositRequest;
import com.theron.wallet.dto.response.DepositResponse;
import com.theron.wallet.dto.response.PixQrCodeResponse;
import com.theron.wallet.service.DepositService;
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
@RequestMapping("/api/v1/deposits")
@RequiredArgsConstructor
@Tag(name = "Deposits", description = "PIX deposit creation and QR code retrieval")
public class DepositController {

    private final DepositService depositService;

    @PostMapping
    @Operation(summary = "Create a PIX deposit",
            description = "Creates a pending deposit transaction and a PIX payment in Asaas. "
                    + "Use the returned transactionId to retrieve the QR code.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Deposit created, awaiting PIX payment"),
            @ApiResponse(responseCode = "400", description = "Validation error"),
            @ApiResponse(responseCode = "404", description = "Customer not found or not synced with Asaas")
    })
    public ResponseEntity<DepositResponse> createPixDeposit(@Valid @RequestBody DepositRequest request) {
        DepositResponse response = depositService.createPixDeposit(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{transactionId}")
    @Operation(summary = "Get deposit by transaction ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Deposit found"),
            @ApiResponse(responseCode = "404", description = "Transaction not found")
    })
    public ResponseEntity<DepositResponse> findById(@PathVariable UUID transactionId) {
        return ResponseEntity.ok(depositService.findById(transactionId));
    }

    @GetMapping("/{transactionId}/pix-qr-code")
    @Operation(summary = "Get PIX QR code for a deposit",
            description = "Returns the base64-encoded QR code image and copia-e-cola payload for a pending deposit")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "QR code retrieved"),
            @ApiResponse(responseCode = "404", description = "Transaction not found or QR code not available")
    })
    public ResponseEntity<PixQrCodeResponse> getPixQrCode(@PathVariable UUID transactionId) {
        return ResponseEntity.ok(depositService.getPixQrCode(transactionId));
    }
}
