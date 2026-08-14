package com.theron.wallet.controller;

import com.theron.wallet.dto.request.LoginRequest;
import com.theron.wallet.dto.request.LogoutRequest;
import com.theron.wallet.dto.request.RefreshTokenRequest;
import com.theron.wallet.dto.response.AuthSessionResponse;
import com.theron.wallet.dto.response.LoginResponse;
import com.theron.wallet.security.ActorResolver;
import com.theron.wallet.security.ClientRequestMetadata;
import com.theron.wallet.security.UserPrincipal;
import com.theron.wallet.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Product and admin authentication, sessions and token refresh")
public class AuthController {

    private final AuthService authService;
    private final ActorResolver actorResolver;

    @PostMapping("/login")
    @Operation(
            summary = "Login",
            description = """
                    Unified login for product users and Theron admins.
                    
                    - Product user (ACTIVE in app_user, password matches): access + refresh + session/device.
                    - Otherwise active AdminUser: dashboard JWT (24h), same fields as before (`token`, `adminId`, `role`).
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Login successful"),
            @ApiResponse(responseCode = "400", description = "Validation error"),
            @ApiResponse(responseCode = "401", description = "Invalid product credentials"),
            @ApiResponse(responseCode = "404", description = "Invalid admin credentials or unknown email")
    })
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        log.info("POST /api/v1/auth/login");
        return ResponseEntity.ok(authService.login(
                request,
                ClientRequestMetadata.clientIp(httpRequest),
                ClientRequestMetadata.userAgent(httpRequest)));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate refresh token", description = "Issues a new access+refresh pair. Reuse of a rotated token revokes all sessions.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "New token pair"),
            @ApiResponse(responseCode = "401", description = "Invalid, expired or reused refresh token")
    })
    public ResponseEntity<LoginResponse> refresh(
            @Valid @RequestBody RefreshTokenRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(authService.refresh(
                request.getRefreshToken(),
                ClientRequestMetadata.clientIp(httpRequest),
                ClientRequestMetadata.userAgent(httpRequest)));
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout current session", description = "Revokes the session from the Bearer access token or from the refresh token in the body.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Session revoked"),
            @ApiResponse(responseCode = "401", description = "Missing credentials")
    })
    public ResponseEntity<Void> logout(@RequestBody(required = false) LogoutRequest request) {
        String refreshToken = request == null ? null : request.getRefreshToken();
        authService.logout(actorResolver.currentPrincipal(), refreshToken);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout-all")
    @Operation(summary = "Revoke all sessions for the authenticated product user")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "All sessions revoked"),
            @ApiResponse(responseCode = "401", description = "Missing or non-product token")
    })
    public ResponseEntity<Void> logoutAll() {
        authService.logoutAll(actorResolver.currentPrincipal());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @Operation(summary = "Current principal", description = "Product users receive UserResponse (no password). Admins receive name/email/role/adminId.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Current user"),
            @ApiResponse(responseCode = "401", description = "Missing or expired token")
    })
    public ResponseEntity<Object> me() {
        return ResponseEntity.ok(authService.me(actorResolver.currentPrincipal()));
    }

    @GetMapping("/sessions")
    @Operation(summary = "List active sessions for the authenticated product user")
    public ResponseEntity<List<AuthSessionResponse>> sessions() {
        return ResponseEntity.ok(authService.listSessions(actorResolver.currentPrincipal()));
    }

    @DeleteMapping("/sessions/{id}")
    @Operation(summary = "Revoke a session owned by the authenticated user")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Session revoked"),
            @ApiResponse(responseCode = "404", description = "Session not found for this user")
    })
    public ResponseEntity<Void> revokeSession(@PathVariable UUID id) {
        authService.revokeSession(actorResolver.currentPrincipal(), id);
        return ResponseEntity.noContent().build();
    }
}
