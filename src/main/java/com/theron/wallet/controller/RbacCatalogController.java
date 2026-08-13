package com.theron.wallet.controller;

import com.theron.wallet.dto.response.PermissionResponse;
import com.theron.wallet.dto.response.RoleResponse;
import com.theron.wallet.repository.PermissionRepository;
import com.theron.wallet.repository.RoleRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "RBAC Catalog", description = "Global role and permission catalog")
public class RbacCatalogController {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;

    @GetMapping("/roles")
    @Operation(summary = "List system roles")
    public ResponseEntity<List<RoleResponse>> listRoles() {
        List<RoleResponse> roles = roleRepository.findAll().stream()
                .sorted(Comparator.comparing(r -> r.getCode()))
                .map(r -> RoleResponse.builder().code(r.getCode()).description(r.getDescription()).build())
                .toList();
        return ResponseEntity.ok(roles);
    }

    @GetMapping("/permissions")
    @Operation(summary = "List system permissions")
    public ResponseEntity<List<PermissionResponse>> listPermissions() {
        List<PermissionResponse> permissions = permissionRepository.findAll().stream()
                .sorted(Comparator.comparing(p -> p.getCode()))
                .map(p -> PermissionResponse.builder().code(p.getCode()).description(p.getDescription()).build())
                .toList();
        return ResponseEntity.ok(permissions);
    }
}
