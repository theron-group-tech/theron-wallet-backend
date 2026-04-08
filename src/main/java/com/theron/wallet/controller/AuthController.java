package com.theron.wallet.controller;

import com.theron.wallet.dto.request.LoginRequest;
import com.theron.wallet.dto.response.LoginResponse;
import com.theron.wallet.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Admin authentication — returns JWT token for dashboard access")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @Operation(
            summary = "Admin login",
            description = """
                    Authenticates an admin user and returns a JWT Bearer token.
                    
                    - Include the token in the `Authorization: Bearer <token>` header for protected routes.
                    - Token expires in 24h (configurable via `JWT_EXPIRATION_MS` env var).
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Login successful — JWT token returned"),
            @ApiResponse(responseCode = "400", description = "Validation error"),
            @ApiResponse(responseCode = "404", description = "Invalid email or password")
    })
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("POST /api/v1/auth/login — email={}", request.getEmail());
        return ResponseEntity.ok(authService.login(request));
    }
}

