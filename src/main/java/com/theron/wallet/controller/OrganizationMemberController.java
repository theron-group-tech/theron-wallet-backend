package com.theron.wallet.controller;

import com.theron.wallet.dto.request.CreateOrganizationEmployeeRequest;
import com.theron.wallet.dto.request.UpdateOrganizationMemberRequest;
import com.theron.wallet.dto.response.OrganizationMembershipResponse;
import com.theron.wallet.dto.response.OrganizationEmployeeResponse;
import com.theron.wallet.enums.MembershipStatus;
import com.theron.wallet.security.Actor;
import com.theron.wallet.security.ActorResolver;
import com.theron.wallet.security.PermissionCodes;
import com.theron.wallet.security.ResourceAuthorization;
import com.theron.wallet.service.OrganizationAdminService;
import com.theron.wallet.service.OrganizationMembershipService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/v1/organizations/{organizationId}/members")
@RequiredArgsConstructor
@Tag(name = "Organization Members", description = "Membership de usuários em organizations — administrativo")
public class OrganizationMemberController {

    private final OrganizationMembershipService membershipService;
    private final OrganizationAdminService organizationAdminService;
    private final ActorResolver actorResolver;
    private final ResourceAuthorization resourceAuthorization;

    @PostMapping
    @Operation(
            summary = "Criar membro",
            description = "Cria um novo usuário na organização, cria sua membership, atribui FINANCE ou EMPLOYEE e provisiona sua Account."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Usuário, membership e Account criados"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos"),
            @ApiResponse(responseCode = "403", description = "Ator sem permissão para gerenciar membros"),
            @ApiResponse(responseCode = "409", description = "Email do usuário já existe ou conflito de recurso")
    })
    public ResponseEntity<OrganizationEmployeeResponse> createMember(
            @PathVariable UUID organizationId,
            @Valid @RequestBody CreateOrganizationEmployeeRequest request) {
        Actor actor = actorResolver.requireActor();
        resourceAuthorization.requireOrganization(actor, organizationId, PermissionCodes.MEMBERS_MANAGE);
        OrganizationEmployeeResponse response =
                organizationAdminService.createEmployee(actor, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "Listar membros", description = "Paginado; filtro opcional por status. Escopo sempre pela organization do path.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista retornada"),
            @ApiResponse(responseCode = "404", description = "Organization não encontrada")
    })
    public ResponseEntity<Page<OrganizationMembershipResponse>> listMembers(
            @PathVariable UUID organizationId,
            @RequestParam(required = false) MembershipStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        resourceAuthorization.requireOrganization(
                actorResolver.requireProductUserId(), organizationId, PermissionCodes.MEMBERS_READ);
        return ResponseEntity.ok(membershipService.listMembers(organizationId, status, pageable));
    }

    @PatchMapping("/{userId}")
    @Operation(summary = "Atualizar status do membro")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Membership atualizada"),
            @ApiResponse(responseCode = "404", description = "Organization, User ou Membership não encontrado")
    })
    public ResponseEntity<OrganizationMembershipResponse> updateMember(
            @PathVariable UUID organizationId,
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateOrganizationMemberRequest request) {
        resourceAuthorization.requireOrganization(
                actorResolver.requireProductUserId(), organizationId, PermissionCodes.MEMBERS_MANAGE);
        return ResponseEntity.ok(membershipService.updateMember(organizationId, userId, request));
    }

    @DeleteMapping("/{userId}")
    @Operation(summary = "Remover membro", description = "Soft-delete: status = REMOVED. Não exclui o User.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Membership marcada como REMOVED"),
            @ApiResponse(responseCode = "404", description = "Organization, User ou Membership não encontrado")
    })
    public ResponseEntity<OrganizationMembershipResponse> removeMember(
            @PathVariable UUID organizationId,
            @PathVariable UUID userId) {
        resourceAuthorization.requireOrganization(
                actorResolver.requireProductUserId(), organizationId, PermissionCodes.MEMBERS_MANAGE);
        return ResponseEntity.ok(membershipService.removeMember(organizationId, userId));
    }
}
