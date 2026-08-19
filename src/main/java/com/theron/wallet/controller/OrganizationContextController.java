package com.theron.wallet.controller;

import com.theron.wallet.dto.request.CreateAccountRequest;
import com.theron.wallet.dto.request.CreateOrganizationEmployeeRequest;
import com.theron.wallet.dto.response.AccountResponse;
import com.theron.wallet.dto.response.OrganizationEmployeeResponse;
import com.theron.wallet.dto.response.OrganizationMembershipResponse;
import com.theron.wallet.dto.response.OrganizationResponse;
import com.theron.wallet.security.ActorResolver;
import com.theron.wallet.service.OrganizationAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organization")
@RequiredArgsConstructor
@Tag(name = "Organization Admin", description = "Organization-scoped APIs resolved from JWT membership")
public class OrganizationContextController {

    private final ActorResolver actorResolver;
    private final OrganizationAdminService organizationAdminService;

    @GetMapping
    @Operation(summary = "Current organization from JWT membership")
    public ResponseEntity<OrganizationResponse> getOrganization() {
        return ResponseEntity.ok(organizationAdminService.getMyOrganization(actorResolver.requireProductUserId()));
    }

    @GetMapping("/members")
    public ResponseEntity<Page<OrganizationMembershipResponse>> listMembers(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(organizationAdminService.listMembers(actorResolver.requireProductUserId(), pageable));
    }

    @PostMapping("/members")
    public ResponseEntity<OrganizationEmployeeResponse> createEmployee(
            @Valid @RequestBody CreateOrganizationEmployeeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(organizationAdminService.createEmployee(actorResolver.requireProductUserId(), request));
    }

    @GetMapping("/accounts")
    public ResponseEntity<List<AccountResponse>> listAccounts() {
        return ResponseEntity.ok(organizationAdminService.listAccounts(actorResolver.requireProductUserId()));
    }

    @PostMapping("/accounts")
    public ResponseEntity<AccountResponse> createOwnAccount(@Valid @RequestBody CreateAccountRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(organizationAdminService.createOwnAccount(actorResolver.requireProductUserId(), request));
    }

    @GetMapping("/accounts/{accountId}")
    public ResponseEntity<AccountResponse> getAccount(@PathVariable UUID accountId) {
        return ResponseEntity.ok(organizationAdminService.getAccount(actorResolver.requireProductUserId(), accountId));
    }
}
