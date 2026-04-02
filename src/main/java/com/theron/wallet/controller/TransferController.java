package com.theron.wallet.controller;

import com.theron.wallet.dto.request.InternalTransferRequest;
import com.theron.wallet.dto.response.InternalTransferResponse;
import com.theron.wallet.service.InternalTransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transfers")
@RequiredArgsConstructor
@Tag(name = "Transfers", description = "Internal wallet-to-wallet transfer operations")
public class TransferController {

    private final InternalTransferService internalTransferService;

    @PostMapping("/internal")
    @Operation(
            summary = "Create an internal transfer",
            description = "Transfers funds between two platform customer wallets atomically, "
                    + "without involving any external payment provider. "
                    + "Supplying the same idempotency key on repeated calls returns the original result.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Transfer completed successfully"),
            @ApiResponse(responseCode = "400", description = "Validation error or self-transfer attempt"),
            @ApiResponse(responseCode = "404", description = "Sender/receiver customer or wallet not found"),
            @ApiResponse(responseCode = "409", description = "Insufficient balance in sender wallet")
    })
    public ResponseEntity<InternalTransferResponse> internalTransfer(
            @Valid @RequestBody InternalTransferRequest request) {
        InternalTransferResponse response = internalTransferService.transfer(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
