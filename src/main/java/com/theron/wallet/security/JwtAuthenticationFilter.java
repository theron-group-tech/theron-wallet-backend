package com.theron.wallet.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.theron.wallet.entity.AuthSession;
import com.theron.wallet.enums.UserStatus;
import com.theron.wallet.exception.ApiErrorResponse;
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
        UserPrincipal principal;
        if (UserPrincipal.PRINCIPAL_USER.equals(principalType)) {
            principal = authenticateProductUser(claims);
            if (principal == null) {
                writeUnauthorized(request, response, "Invalid or revoked session");
                return;
            }
        } else {
            principal = UserPrincipal.builder()
                    .principalType(UserPrincipal.PRINCIPAL_ADMIN)
                    .email(claims.getSubject())
                    .role(claims.get(JwtTokenProvider.CLAIM_ROLE, String.class))
                    .build();
        }

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + (principal.getRole() != null ? principal.getRole() : "USER")))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
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
        if (response.isCommitted()) {
            return;
        }
        ApiErrorResponse body = ApiErrorResponse.of(
                HttpServletResponse.SC_UNAUTHORIZED,
                "UNAUTHORIZED",
                "Unauthorized",
                message,
                request.getRequestURI());
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
