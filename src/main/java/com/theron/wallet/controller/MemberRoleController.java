package com.theron.wallet.controller;

import com.theron.wallet.dto.request.ReplaceMemberRolesRequest;
import com.theron.wallet.security.ActorResolver;
import com.theron.wallet.service.RoleAssignmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/members/{userId}")
@RequiredArgsConstructor
@Tag(name = "Member Roles", description = "RBAC role assignment. Actor comes from JWT when present; X-Actor-User-Id is fallback.")
public class MemberRoleController {

    public static final String ACTOR_HEADER = "X-Actor-User-Id";

    private final RoleAssignmentService roleAssignmentService;
    private final ActorResolver actorResolver;

    @GetMapping("/roles")
    @Operation(summary = "List member roles")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Roles returned"),
            @ApiResponse(responseCode = "403", description = "Missing members.read (or self org.read)")
    })
    public ResponseEntity<List<String>> listRoles(
            @PathVariable UUID organizationId,
            @PathVariable UUID userId,
            @RequestHeader(value = ACTOR_HEADER, required = false) UUID actorUserId) {
        return ResponseEntity.ok(roleAssignmentService.listRoles(
                actorResolver.requireProductUserId(actorUserId), organizationId, userId));
    }

    @PutMapping("/roles")
    @Operation(summary = "Replace member roles", description = "Requires members.manage. Only OWNER can grant OWNER.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Roles updated"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "404", description = "Membership not found")
    })
    public ResponseEntity<List<String>> replaceRoles(
            @PathVariable UUID organizationId,
            @PathVariable UUID userId,
            @RequestHeader(value = ACTOR_HEADER, required = false) UUID actorUserId,
            @Valid @RequestBody ReplaceMemberRolesRequest request) {
        // organizationId from path only — body cannot change tenant; actor never from body
        return ResponseEntity.ok(roleAssignmentService.replaceRoles(
                actorResolver.requireProductUserId(actorUserId), organizationId, userId, request.getRoleCodes()));
    }

    @GetMapping("/permissions")
    @Operation(summary = "List effective permissions for member")
    public ResponseEntity<List<String>> listPermissions(
            @PathVariable UUID organizationId,
            @PathVariable UUID userId,
            @RequestHeader(value = ACTOR_HEADER, required = false) UUID actorUserId) {
        return ResponseEntity.ok(roleAssignmentService.listPermissions(
                actorResolver.requireProductUserId(actorUserId), organizationId, userId));
    }
}
