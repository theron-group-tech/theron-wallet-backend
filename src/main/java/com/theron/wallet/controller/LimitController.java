package com.theron.wallet.controller;

import com.theron.wallet.dto.request.CreateTransactionLimitRequest;
import com.theron.wallet.dto.request.UpdateTransactionLimitRequest;
import com.theron.wallet.dto.response.TransactionLimitResponse;
import com.theron.wallet.security.ActorResolver;
import com.theron.wallet.service.TransactionLimitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/limits")
@RequiredArgsConstructor
@Tag(name = "Transaction Limits", description = "Hierarchical financial limits. Requires JWT access token.")
public class LimitController {
    private final TransactionLimitService transactionLimitService;
    private final ActorResolver actorResolver;

    @PostMapping
    @Operation(summary = "Create transaction limit", description = "Requires limits.manage")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Limit created"),
            @ApiResponse(responseCode = "403", description = "Missing permission or cross-org scope"),
            @ApiResponse(responseCode = "409", description = "Duplicate scope+type+period")
    })
    public ResponseEntity<TransactionLimitResponse> create(
            @Valid @RequestBody CreateTransactionLimitRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(transactionLimitService.create(actorResolver.requireProductUserId(), request));
    }

    @GetMapping
    @Operation(summary = "List limits by organization", description = "Requires limits.read")
    public ResponseEntity<List<TransactionLimitResponse>> list(
            @RequestParam UUID organizationId) {
        return ResponseEntity.ok(transactionLimitService.listByOrganization(
                actorResolver.requireProductUserId(), organizationId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get limit by id", description = "Requires limits.read")
    public ResponseEntity<TransactionLimitResponse> getById(
            @PathVariable UUID id
            ) {
        return ResponseEntity.ok(transactionLimitService.getById(
                actorResolver.requireProductUserId(), id));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update limit", description = "Requires limits.manage")
    public ResponseEntity<TransactionLimitResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTransactionLimitRequest request) {
        return ResponseEntity.ok(transactionLimitService.update(
                actorResolver.requireProductUserId(), id, request));
    }
}
