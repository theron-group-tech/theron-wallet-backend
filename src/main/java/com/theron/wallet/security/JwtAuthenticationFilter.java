package com.theron.wallet.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.theron.wallet.entity.AdminUser;
import com.theron.wallet.entity.AuthSession;
import com.theron.wallet.entity.OauthClient;
import com.theron.wallet.enums.UserStatus;
import com.theron.wallet.exception.ApiErrorResponse;
import com.theron.wallet.repository.AdminUserRepository;
import com.theron.wallet.repository.AuthSessionRepository;
import com.theron.wallet.service.impl.OauthClientLoader;
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
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final AuthSessionRepository authSessionRepository;
    private final AdminUserRepository adminUserRepository;
    private final OauthClientLoader oauthClientLoader;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(7).trim();

        if (token.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        final Claims claims;

        try {
            claims = jwtTokenProvider.parseClaims(token);
        } catch (ExpiredJwtException ex) {
            writeUnauthorized(request, response, "Access token expired");
            return;
        } catch (Exception ex) {
            writeUnauthorized(request, response, "Invalid access token");
            return;
        }

        String principalType = jwtTokenProvider.getPrincipalType(claims);

        if (ClientPrincipal.PRINCIPAL_CLIENT.equals(principalType)) {
            ClientPrincipal client = authenticateClient(claims);
            if (client == null) {
                writeUnauthorized(request, response, "Invalid or revoked client credentials");
                return;
            }
            List<SimpleGrantedAuthority> authorities = client.getScopes().stream()
                    .map(scope -> new SimpleGrantedAuthority("SCOPE_" + scope))
                    .collect(Collectors.toList());
            authorities.add(new SimpleGrantedAuthority("ROLE_CLIENT"));
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(client, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
            return;
        }

        UserPrincipal principal;

        if (UserPrincipal.PRINCIPAL_USER.equals(principalType)) {
            principal = authenticateProductUser(claims);
            if (principal == null) {
                writeUnauthorized(request, response, "Invalid or revoked session");
                return;
            }
        } else {
            String email = claims.getSubject();
            AdminUser admin = adminUserRepository
                    .findByEmailAndActiveTrue(email)
                    .orElse(null);
            if (admin == null) {
                writeUnauthorized(request, response, "Authentication required");
                return;
            }
            principal = UserPrincipal.builder()
                    .principalType(UserPrincipal.PRINCIPAL_ADMIN)
                    .adminId(admin.getId())
                    .email(admin.getEmail())
                    .role(admin.getRole())
                    .build();
        }

        String role = principal.getRole() != null ? principal.getRole() : "USER";
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role))
                );
        SecurityContextHolder.getContext().setAuthentication(authentication);
        filterChain.doFilter(request, response);
    }

    private ClientPrincipal authenticateClient(Claims claims) {
        UUID oauthClientId;
        try {
            oauthClientId = UUID.fromString(claims.getSubject());
        } catch (IllegalArgumentException ex) {
            return null;
        }
        OauthClient client = oauthClientLoader.loadActiveWithDetailsById(oauthClientId).orElse(null);
        if (client == null) {
            return null;
        }
        return ClientPrincipal.builder()
                .oauthClientId(client.getId())
                .publicClientId(client.getClientId())
                .organizationId(client.getOrganization().getId())
                .name(client.getName())
                .scopes(OauthClientLoader.scopeCodes(client))
                .allowedAccountIds(OauthClientLoader.accountIds(client))
                .build();
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
