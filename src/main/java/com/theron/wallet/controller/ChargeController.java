package com.theron.wallet.controller;

import com.theron.wallet.dto.request.CreateChargeRequest;
import com.theron.wallet.dto.response.ChargeResponse;
import com.theron.wallet.security.Actor;
import com.theron.wallet.security.ActorResolver;
import com.theron.wallet.service.ChargeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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
@RequestMapping("/api/v1/charges")
@RequiredArgsConstructor
@Tag(name = "Charges", description = "Multi-tenant Asaas billing charges (account from OAuth credentials)")
public class ChargeController {

    private final ActorResolver actorResolver;
    private final ChargeService chargeService;

    @PostMapping
    @Operation(summary = "Create charge (idempotent by externalReference per account)")
    public ResponseEntity<ChargeResponse> create(@Valid @RequestBody CreateChargeRequest request) {
        Actor actor = actorResolver.requireActor();
        return ResponseEntity.status(HttpStatus.CREATED).body(chargeService.create(actor, request));
    }

    @GetMapping
    @Operation(summary = "List charges for the authenticated account")
    public ResponseEntity<Page<ChargeResponse>> list(@PageableDefault(size = 20) Pageable pageable) {
        Actor actor = actorResolver.requireActor();
        return ResponseEntity.ok(chargeService.list(actor, pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get charge by id (tenant-scoped)")
    public ResponseEntity<ChargeResponse> get(@PathVariable UUID id) {
        Actor actor = actorResolver.requireActor();
        return ResponseEntity.ok(chargeService.get(actor, id));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel charge in Asaas and locally")
    public ResponseEntity<ChargeResponse> cancel(@PathVariable UUID id) {
        Actor actor = actorResolver.requireActor();
        return ResponseEntity.ok(chargeService.cancel(actor, id));
    }
}
