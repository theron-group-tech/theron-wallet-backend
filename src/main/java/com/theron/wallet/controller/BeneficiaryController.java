package com.theron.wallet.controller;

import com.theron.wallet.dto.request.CreateBeneficiaryRequest;
import com.theron.wallet.dto.request.UpdateBeneficiaryRequest;
import com.theron.wallet.dto.response.BeneficiaryResponse;
import com.theron.wallet.security.ActorResolver;
import com.theron.wallet.service.BeneficiaryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/v1/beneficiaries")
@RequiredArgsConstructor
@Tag(name = "Beneficiaries", description = "Organization payees. Actor comes from JWT when present; X-Actor-User-Id is fallback.")
public class BeneficiaryController {

    public static final String ACTOR_HEADER = "X-Actor-User-Id";

    private final BeneficiaryService beneficiaryService;
    private final ActorResolver actorResolver;

    @PostMapping
    @Operation(summary = "Create beneficiary", description = "Requires beneficiaries.create. organizationId comes from the body.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Beneficiary created"),
            @ApiResponse(responseCode = "401", description = "Missing actor"),
            @ApiResponse(responseCode = "403", description = "Missing permission"),
            @ApiResponse(responseCode = "409", description = "Duplicate PIX key or bank account in the organization")
    })
    public ResponseEntity<BeneficiaryResponse> create(
            @RequestHeader(value = ACTOR_HEADER, required = false) UUID actorUserId,
            @Valid @RequestBody CreateBeneficiaryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(beneficiaryService.create(actorResolver.requireProductUserId(actorUserId), request));
    }

    @GetMapping
    @Operation(summary = "List beneficiaries", description = "Requires beneficiaries.read. organizationId is mandatory.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Beneficiaries returned"),
            @ApiResponse(responseCode = "400", description = "organizationId missing"),
            @ApiResponse(responseCode = "403", description = "Missing permission")
    })
    public ResponseEntity<List<BeneficiaryResponse>> list(
            @RequestHeader(value = ACTOR_HEADER, required = false) UUID actorUserId,
            @RequestParam UUID organizationId) {
        return ResponseEntity.ok(beneficiaryService.listByOrganization(
                actorResolver.requireProductUserId(actorUserId), organizationId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get beneficiary by id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Beneficiary found"),
            @ApiResponse(responseCode = "403", description = "Actor is not a member of the owner organization"),
            @ApiResponse(responseCode = "404", description = "Beneficiary not found")
    })
    public ResponseEntity<BeneficiaryResponse> findById(
            @PathVariable UUID id,
            @RequestHeader(value = ACTOR_HEADER, required = false) UUID actorUserId) {
        return ResponseEntity.ok(beneficiaryService.findById(actorResolver.requireProductUserId(actorUserId), id));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update beneficiary", description = "Partial update. organizationId is immutable.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Beneficiary updated"),
            @ApiResponse(responseCode = "403", description = "Missing permission"),
            @ApiResponse(responseCode = "404", description = "Beneficiary not found")
    })
    public ResponseEntity<BeneficiaryResponse> update(
            @PathVariable UUID id,
            @RequestHeader(value = ACTOR_HEADER, required = false) UUID actorUserId,
            @Valid @RequestBody UpdateBeneficiaryRequest request) {
        return ResponseEntity.ok(beneficiaryService.update(
                actorResolver.requireProductUserId(actorUserId), id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Logically delete beneficiary", description = "Sets status to INACTIVE. Historical transactions keep the FK.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Beneficiary inactivated"),
            @ApiResponse(responseCode = "403", description = "Missing permission"),
            @ApiResponse(responseCode = "404", description = "Beneficiary not found")
    })
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @RequestHeader(value = ACTOR_HEADER, required = false) UUID actorUserId) {
        beneficiaryService.delete(actorResolver.requireProductUserId(actorUserId), id);
        return ResponseEntity.noContent().build();
    }
}
