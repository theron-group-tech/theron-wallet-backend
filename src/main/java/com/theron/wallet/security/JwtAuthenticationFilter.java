package com.theron.wallet.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.theron.wallet.entity.AdminUser;
import com.theron.wallet.entity.AuthSession;
import com.theron.wallet.enums.UserStatus;
import com.theron.wallet.exception.ApiErrorResponse;
import com.theron.wallet.repository.AdminUserRepository;
import com.theron.wallet.repository.AuthSessionRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final AuthSessionRepository authSessionRepository;
    private final AdminUserRepository adminUserRepository;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        log.info("JWT FILTER - {} {}", request.getMethod(), request.getRequestURI());
        log.info("JWT FILTER - Authorization header present: {}", header != null);

        if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            log.warn("JWT FILTER - No Bearer token found");
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(7).trim();

        if (token.isEmpty()) {
            log.warn("JWT FILTER - Bearer token is empty");
            filterChain.doFilter(request, response);
            return;
        }

        final Claims claims;

        try {
            claims = jwtTokenProvider.parseClaims(token);

            log.info(
                    "JWT FILTER - Token valid. subject={}, principal={}, role={}, typ={}, sid={}",
                    claims.getSubject(),
                    claims.get("principal"),
                    claims.get("role"),
                    claims.get("typ"),
                    claims.get("sid")
            );

        } catch (ExpiredJwtException ex) {
            log.error("JWT FILTER - Token expired", ex);
            writeUnauthorized(request, response, "Access token expired");
            return;

        } catch (Exception ex) {
            log.error("JWT FILTER - Token parsing failed: {}", ex.getMessage(), ex);
            writeUnauthorized(request, response, "Invalid access token");
            return;
        }

        String principalType = jwtTokenProvider.getPrincipalType(claims);

        log.info("JWT FILTER - Principal type: {}", principalType);

        UserPrincipal principal;

        if (UserPrincipal.PRINCIPAL_USER.equals(principalType)) {

            log.info("JWT FILTER - Authenticating PRODUCT USER");

            principal = authenticateProductUser(claims);

            if (principal == null) {
                log.error("JWT FILTER - Product user authentication failed");
                writeUnauthorized(request, response, "Invalid or revoked session");
                return;
            }

        } else {

            String email = claims.getSubject();

            log.info("JWT FILTER - Authenticating ADMIN: {}", email);

            AdminUser admin = adminUserRepository
                    .findByEmailAndActiveTrue(email)
                    .orElse(null);

            if (admin == null) {
                log.error(
                        "JWT FILTER - Admin NOT FOUND or inactive. email={}",
                        email
                );

                writeUnauthorized(request, response, "Authentication required");
                return;
            }

            log.info(
                    "JWT FILTER - Admin authenticated successfully. id={}, email={}, role={}",
                    admin.getId(),
                    admin.getEmail(),
                    admin.getRole()
            );

            principal = UserPrincipal.builder()
                    .principalType(UserPrincipal.PRINCIPAL_ADMIN)
                    .adminId(admin.getId())
                    .email(admin.getEmail())
                    .role(admin.getRole())
                    .build();
        }

        String role = principal.getRole() != null
                ? principal.getRole()
                : "USER";

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role))
                );

        SecurityContextHolder.getContext().setAuthentication(authentication);

        log.info(
                "JWT FILTER - SecurityContext authenticated. principal={}, authorities={}",
                principal.getEmail(),
                authentication.getAuthorities()
        );

        filterChain.doFilter(request, response);
    }

    private UserPrincipal authenticateProductUser(Claims claims) {
        UUID sessionId = jwtTokenProvider.getSessionId(claims);
        if (sessionId == null) {
            return null;
        }
        AuthSession session = authSessionRepository.findWithUserById(sessionId).orElse(null);
        if (session == null || session.getRevokedAt() != null) {
            return null;
        }
        if (session.getUser() == null || session.getUser().getStatus() != UserStatus.ACTIVE) {
            return null;
        }
        UUID userId;
        try {
            userId = UUID.fromString(claims.getSubject());
        } catch (IllegalArgumentException ex) {
            return null;
        }
        if (!userId.equals(session.getUser().getId())) {
            return null;
        }
        return UserPrincipal.builder()
                .principalType(UserPrincipal.PRINCIPAL_USER)
                .userId(userId)
                .sessionId(sessionId)
                .email(session.getUser().getEmail())
                .name(session.getUser().getName())
                .build();
    }

    public void writeUnauthorized(HttpServletRequest request, HttpServletResponse response, String message)
            throws IOException {
        writeError(request, response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED", "Unauthorized", message);
    }

    public void writeForbidden(HttpServletRequest request, HttpServletResponse response, String message)
            throws IOException {
        writeError(request, response, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN", "Forbidden", message);
    }

    private void writeError(
            HttpServletRequest request,
            HttpServletResponse response,
            int status,
            String code,
            String error,
            String message) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        ApiErrorResponse body = ApiErrorResponse.of(
                status,
                code,
                error,
                message,
                request.getRequestURI());
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
