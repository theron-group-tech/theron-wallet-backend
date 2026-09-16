package com.theron.wallet.controller;

import com.theron.wallet.dto.request.CreateOrganizationRequest;
import com.theron.wallet.dto.request.UpdateOrganizationRequest;
import com.theron.wallet.dto.request.UpdateOrganizationStatusRequest;
import com.theron.wallet.dto.response.OrganizationResponse;
import com.theron.wallet.dto.response.OrganizationStatusResponse;
import com.theron.wallet.enums.OrganizationStatus;
import com.theron.wallet.exception.ForbiddenException;
import com.theron.wallet.security.ActorResolver;
import com.theron.wallet.security.PermissionCodes;
import com.theron.wallet.security.ResourceAuthorization;
import com.theron.wallet.service.AuthorizationService;
import com.theron.wallet.service.OrganizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organizations")
@RequiredArgsConstructor
@Tag(name = "Organizations", description = "Gerenciamento de empresas clientes (tenants) da Theron — administrativo")
public class OrganizationController {

    private final OrganizationService organizationService;
    private final ActorResolver actorResolver;
    private final ResourceAuthorization resourceAuthorization;
    private final AuthorizationService authorizationService;

    @PostMapping
    @Operation(summary = "Criar organization", description = "Cria uma empresa cliente com status ACTIVE. Não provisiona Asaas.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Organization criada"),
            @ApiResponse(responseCode = "400", description = "Erro de validação"),
            @ApiResponse(responseCode = "409", description = "Documento já cadastrado"),
            @ApiResponse(responseCode = "422", description = "Documento inválido para o tipo informado")
    })
    public ResponseEntity<OrganizationResponse> create(@Valid @RequestBody CreateOrganizationRequest request) {
        actorResolver.requireProductUserId();
        throw new ForbiddenException("Organizations can only be created by the platform administrator");
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar organization por ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Organization encontrada"),
            @ApiResponse(responseCode = "404", description = "Organization não encontrada")
    })
    public ResponseEntity<OrganizationResponse> findById(@PathVariable UUID id) {
        resourceAuthorization.requireOrganization(
                actorResolver.requireActor(), id, PermissionCodes.ORGANIZATION_READ);
        return ResponseEntity.ok(organizationService.findById(id));
    }

    @PatchMapping("/{id}")
    @Operation(
            summary = "Atualizar organization",
            description = "Atualiza legalName, tradeName e/ou status. O document é imutável."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Organization atualizada"),
            @ApiResponse(responseCode = "404", description = "Organization não encontrada"),
            @ApiResponse(responseCode = "422", description = "Dados inválidos")
    })
    public ResponseEntity<OrganizationResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateOrganizationRequest request) {
        resourceAuthorization.requireOrganization(
                actorResolver.requireProductUserId(), id, PermissionCodes.ORGANIZATION_UPDATE);
        return ResponseEntity.ok(organizationService.update(id, request));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Alterar status da organization")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Status atualizado"),
            @ApiResponse(responseCode = "404", description = "Organization não encontrada")
    })
    public ResponseEntity<OrganizationStatusResponse> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateOrganizationStatusRequest request) {
        resourceAuthorization.requireOrganization(
                actorResolver.requireProductUserId(), id, PermissionCodes.ORGANIZATION_UPDATE);
        return ResponseEntity.ok(organizationService.updateStatus(id, request));
    }

    @GetMapping
    @Operation(
            summary = "Listar organizations (paginado)",
            description = "Suporta filtro opcional por status. Exemplo: `?status=ACTIVE&page=0&size=20&sort=createdAt,desc`"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista retornada com sucesso")
    })
    public ResponseEntity<Page<OrganizationResponse>> findAll(
            @RequestParam(required = false) OrganizationStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        UUID actor = actorResolver.requireProductUserId();
        Page<OrganizationResponse> page = organizationService.findAll(status, pageable);
        var visible = page.getContent().stream()
                .filter(org -> authorizationService.hasPermission(
                        org.getId(), actor, PermissionCodes.ORGANIZATION_READ))
                .toList();
        return ResponseEntity.ok(new PageImpl<>(visible, pageable, visible.size()));
    }

    @GetMapping("/{id}/status")
    @Operation(summary = "Consultar status da organization")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Status retornado"),
            @ApiResponse(responseCode = "404", description = "Organization não encontrada")
    })
    public ResponseEntity<OrganizationStatusResponse> getStatus(@PathVariable UUID id) {
        resourceAuthorization.requireOrganization(
                actorResolver.requireProductUserId(), id, PermissionCodes.ORGANIZATION_READ);
        return ResponseEntity.ok(organizationService.getStatus(id));
    }
}
