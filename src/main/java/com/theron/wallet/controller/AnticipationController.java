package com.theron.wallet.controller;

import com.theron.wallet.dto.request.CreateAnticipationRequest;
import com.theron.wallet.dto.response.AnticipationResponse;
import com.theron.wallet.security.Actor;
import com.theron.wallet.security.ActorResolver;
import com.theron.wallet.service.AnticipationService;
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
@RequestMapping("/api/v1/anticipations")
@RequiredArgsConstructor
@Tag(name = "Anticipations", description = "Receivable anticipation (account from OAuth credentials)")
public class AnticipationController {

    private final ActorResolver actorResolver;
    private final AnticipationService anticipationService;

    @PostMapping("/simulate")
    @Operation(summary = "Simulate anticipation for charges/payments of the bound account")
    public ResponseEntity<AnticipationResponse> simulate(@Valid @RequestBody CreateAnticipationRequest request) {
        Actor actor = actorResolver.requireActor();
        return ResponseEntity.ok(anticipationService.simulate(actor, request));
    }

    @PostMapping
    @Operation(summary = "Create anticipation")
    public ResponseEntity<AnticipationResponse> create(@Valid @RequestBody CreateAnticipationRequest request) {
        Actor actor = actorResolver.requireActor();
        return ResponseEntity.status(HttpStatus.CREATED).body(anticipationService.create(actor, request));
    }

    @GetMapping
    @Operation(summary = "List anticipations for the authenticated account")
    public ResponseEntity<Page<AnticipationResponse>> list(@PageableDefault(size = 20) Pageable pageable) {
        Actor actor = actorResolver.requireActor();
        return ResponseEntity.ok(anticipationService.list(actor, pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get anticipation by id")
    public ResponseEntity<AnticipationResponse> get(@PathVariable UUID id) {
        Actor actor = actorResolver.requireActor();
        return ResponseEntity.ok(anticipationService.get(actor, id));
    }
}
