package com.theron.wallet.controller.admin;

import com.theron.wallet.dto.request.AssignOrganizationAdminRequest;
import com.theron.wallet.dto.request.CreateAdminOwnerRequest;
import com.theron.wallet.dto.request.CreateOrganizationRequest;
import com.theron.wallet.dto.request.UpdateOrganizationRequest;
import com.theron.wallet.dto.request.UpdateSplitConfigRequest;
import com.theron.wallet.dto.response.AdminMeResponse;
import com.theron.wallet.dto.response.AdminOrganizationDetailResponse;
import com.theron.wallet.dto.response.AdminOwnerResponse;
import com.theron.wallet.dto.response.AdminTransactionResponse;
import com.theron.wallet.dto.response.AsaasBindResponse;
import com.theron.wallet.dto.response.BalanceDivergenceResponse;
import com.theron.wallet.dto.response.InboundDedupeResponse;
import com.theron.wallet.dto.response.InboundReconcileResponse;
import com.theron.wallet.dto.response.OrganizationMembershipResponse;
import com.theron.wallet.dto.response.OrganizationResponse;
import com.theron.wallet.dto.response.PlatformAccountResponse;
import com.theron.wallet.dto.response.SplitConfigResponse;
import com.theron.wallet.dto.response.SubaccountWebhookRepairResponse;
import com.theron.wallet.enums.AsaasBindStatus;
import com.theron.wallet.enums.OrganizationStatus;
import com.theron.wallet.enums.TransactionStatus;
import com.theron.wallet.enums.TransactionType;
import com.theron.wallet.security.ActorResolver;
import com.theron.wallet.security.UserPrincipal;
import com.theron.wallet.service.AdminPlatformService;
import com.theron.wallet.service.AsaasSubaccountWebhookService;
import com.theron.wallet.service.InboundPixDedupeService;
import com.theron.wallet.service.InboundPixReconcileService;
import com.theron.wallet.service.PlatformAccountService;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Tag(name = "Platform Admin", description = "Cross-tenant APIs for the platform administrator")
public class AdminPlatformController {

    private final ActorResolver actorResolver;
    private final AdminPlatformService adminPlatformService;
    private final PlatformAccountService platformAccountService;
    private final AsaasSubaccountWebhookService asaasSubaccountWebhookService;
    private final InboundPixReconcileService inboundPixReconcileService;
    private final InboundPixDedupeService inboundPixDedupeService;

    @GetMapping("/me")
    @Operation(summary = "Platform admin identity")
    public ResponseEntity<AdminMeResponse> me() {
        UserPrincipal principal = actorResolver.requireAdmin();
        return ResponseEntity.ok(AdminMeResponse.builder()
                .adminId(principal.getAdminId())
                .email(principal.getEmail())
                .role(principal.getRole())
                .build());
    }

    @GetMapping("/organizations")
    public ResponseEntity<Page<OrganizationResponse>> listOrganizations(
            @RequestParam(required = false) OrganizationStatus status,
            @RequestParam(required = false) String document,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        actorResolver.requireAdmin();
        return ResponseEntity.ok(adminPlatformService.listOrganizations(status, document, q, pageable));
    }

    @PostMapping("/organizations")
    public ResponseEntity<OrganizationResponse> createOrganization(
            @Valid @RequestBody CreateOrganizationRequest request) {
        UserPrincipal admin = actorResolver.requireAdmin();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(adminPlatformService.createOrganization(request, admin.getAdminId()));
    }

    @GetMapping("/organizations/{id}")
    public ResponseEntity<AdminOrganizationDetailResponse> getOrganization(@PathVariable UUID id) {
        actorResolver.requireAdmin();
        return ResponseEntity.ok(adminPlatformService.getOrganization(id));
    }

    @PatchMapping("/organizations/{id}")
    public ResponseEntity<OrganizationResponse> updateOrganization(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateOrganizationRequest request) {
        UserPrincipal admin = actorResolver.requireAdmin();
        return ResponseEntity.ok(adminPlatformService.updateOrganization(id, request, admin.getAdminId()));
    }

    @GetMapping("/organizations/{id}/members")
    public ResponseEntity<Page<OrganizationMembershipResponse>> listMembers(
            @PathVariable UUID id,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        actorResolver.requireAdmin();
        return ResponseEntity.ok(adminPlatformService.listMembers(id, pageable));
    }

    @PostMapping("/organizations/{id}/admin")
    public ResponseEntity<OrganizationMembershipResponse> assignAdmin(
            @PathVariable UUID id,
            @Valid @RequestBody AssignOrganizationAdminRequest request) {
        UserPrincipal admin = actorResolver.requireAdmin();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(adminPlatformService.assignOrganizationAdmin(id, request, admin.getAdminId()));
    }

    @PostMapping("/organizations/{id}/owners")
    @Operation(summary = "Create organization OWNER (user + membership + account + Asaas provision)")
    public ResponseEntity<AdminOwnerResponse> createOwner(
            @PathVariable UUID id,
            @Valid @RequestBody CreateAdminOwnerRequest request) {
        UserPrincipal admin = actorResolver.requireAdmin();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(adminPlatformService.createOrganizationOwner(id, request, admin.getAdminId()));
    }

    @GetMapping("/accounts")
    public ResponseEntity<Page<AdminOrganizationDetailResponse.AdminAccountSummary>> listAccounts(
            @RequestParam(required = false) UUID organizationId,
            @RequestParam(required = false) AsaasBindStatus asaasStatus,
            @PageableDefault(size = 20) Pageable pageable) {
        actorResolver.requireAdmin();
        return ResponseEntity.ok(adminPlatformService.listAccounts(organizationId, asaasStatus, pageable));
    }

    @GetMapping("/subaccounts")
    public ResponseEntity<Page<AsaasBindResponse>> listSubaccounts(
            @PageableDefault(size = 20) Pageable pageable) {
        actorResolver.requireAdmin();
        return ResponseEntity.ok(adminPlatformService.listSubaccountBinds(pageable));
    }

    @PostMapping("/accounts/{accountId}/asaas-subaccount")
    public ResponseEntity<AsaasBindResponse> provision(
            @PathVariable UUID accountId) {
        UserPrincipal admin = actorResolver.requireAdmin();
        return ResponseEntity.ok(adminPlatformService.provisionSubaccount(accountId, admin.getAdminId()));
    }

    @GetMapping("/transactions")
    public ResponseEntity<Page<AdminTransactionResponse>> listTransactions(
            @RequestParam(required = false) UUID organizationId,
            @RequestParam(required = false) UUID accountId,
            @RequestParam(required = false) LocalDateTime from,
            @RequestParam(required = false) LocalDateTime to,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) TransactionStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        actorResolver.requireAdmin();
        return ResponseEntity.ok(adminPlatformService.listTransactions(
                organizationId, accountId, from, to, type, status, pageable));
    }

    @GetMapping("/transactions/{id}")
    public ResponseEntity<AdminTransactionResponse> getTransaction(@PathVariable UUID id) {
        actorResolver.requireAdmin();
        return ResponseEntity.ok(adminPlatformService.getTransaction(id));
    }

    @GetMapping("/splits")
    public ResponseEntity<SplitConfigResponse> getSplit() {
        actorResolver.requireAdmin();
        return ResponseEntity.ok(adminPlatformService.getSplit());
    }

    @PatchMapping("/splits")
    public ResponseEntity<SplitConfigResponse> updateSplit(@Valid @RequestBody UpdateSplitConfigRequest request) {
        UserPrincipal admin = actorResolver.requireAdmin();
        return ResponseEntity.ok(adminPlatformService.updateSplit(request, admin.getAdminId()));
    }

    @GetMapping("/platform-account")
    @Operation(summary = "Platform Account linked to Asaas Master wallet")
    public ResponseEntity<PlatformAccountResponse> getPlatformAccount() {
        actorResolver.requireAdmin();
        return ResponseEntity.ok(platformAccountService.get());
    }

    @GetMapping("/platform-account/balance-divergences")
    @Operation(summary = "Accounts where Asaas balance diverges from local ledger")
    public ResponseEntity<BalanceDivergenceResponse> balanceDivergences() {
        actorResolver.requireAdmin();
        return ResponseEntity.ok(adminPlatformService.listBalanceDivergences());
    }

    @PostMapping("/subaccounts/{subaccountId}/webhooks/repair")
    @Operation(summary = "Register PAYMENT_*/TRANSFER_* webhooks on an Asaas subaccount")
    public ResponseEntity<SubaccountWebhookRepairResponse> repairSubaccountWebhook(
            @PathVariable UUID subaccountId) {
        actorResolver.requireAdmin();
        return ResponseEntity.ok(asaasSubaccountWebhookService.repairForSubaccount(subaccountId));
    }

    @PostMapping("/subaccounts/webhooks/repair-all")
    @Operation(summary = "Repair Asaas webhooks for all ACTIVE subaccounts")
    public ResponseEntity<SubaccountWebhookRepairResponse> repairAllSubaccountWebhooks() {
        actorResolver.requireAdmin();
        return ResponseEntity.ok(asaasSubaccountWebhookService.repairAllActive());
    }

    @PostMapping("/accounts/{accountId}/inbound-reconcile")
    @Operation(summary = "Credit orphan RECEIVED/CONFIRMED Asaas payments onto Account ledger")
    public ResponseEntity<InboundReconcileResponse> reconcileInboundPix(@PathVariable UUID accountId) {
        actorResolver.requireAdmin();
        return ResponseEntity.ok(inboundPixReconcileService.reconcileAccount(accountId));
    }

    @PostMapping("/accounts/{accountId}/inbound-dedupe")
    @Operation(summary = "Reverse duplicate Master→Theron inbound credits (pay_* when platform_pix already credited)")
    public ResponseEntity<InboundDedupeResponse> dedupeInboundPix(@PathVariable UUID accountId) {
        actorResolver.requireAdmin();
        return ResponseEntity.ok(inboundPixDedupeService.dedupeAccount(accountId));
    }
}
