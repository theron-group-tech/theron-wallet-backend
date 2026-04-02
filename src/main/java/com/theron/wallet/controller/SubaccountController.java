package com.theron.wallet.controller;

import com.theron.wallet.dto.request.CreateSubaccountRequest;
import com.theron.wallet.dto.response.SubaccountResponse;
import com.theron.wallet.service.SubaccountService;
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
@RequestMapping("/api/v1/subaccounts")
@RequiredArgsConstructor
@Tag(name = "Subaccounts", description = "Asaas subaccount (conta-filha) management — backoffice only")
public class SubaccountController {

    private final SubaccountService subaccountService;

    @PostMapping
    @Operation(summary = "Create a subaccount",
            description = "Creates a subaccount in Asaas for the given customer. "
                    + "The customer must already be synced with Asaas. "
                    + "API key is encrypted and stored — never returned in responses.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Subaccount created, pending Asaas evaluation"),
            @ApiResponse(responseCode = "400", description = "Validation error"),
            @ApiResponse(responseCode = "404", description = "Customer not found or not synced with Asaas"),
            @ApiResponse(responseCode = "409", description = "Customer already has a subaccount")
    })
    public ResponseEntity<SubaccountResponse> create(@Valid @RequestBody CreateSubaccountRequest request) {
        SubaccountResponse response = subaccountService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{subaccountId}")
    @Operation(summary = "Get subaccount by ID",
            description = "Returns subaccount details. API key is never included in the response.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Subaccount found"),
            @ApiResponse(responseCode = "404", description = "Subaccount not found")
    })
    public ResponseEntity<SubaccountResponse> findById(@PathVariable UUID subaccountId) {
        return ResponseEntity.ok(subaccountService.findById(subaccountId));
    }

    @GetMapping("/customer/{customerId}")
    @Operation(summary = "Get subaccount by customer ID",
            description = "Returns the subaccount linked to the given customer, if any.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Subaccount found"),
            @ApiResponse(responseCode = "404", description = "No subaccount for this customer")
    })
    public ResponseEntity<SubaccountResponse> findByCustomerId(@PathVariable UUID customerId) {
        return ResponseEntity.ok(subaccountService.findByCustomerId(customerId));
    }
}
