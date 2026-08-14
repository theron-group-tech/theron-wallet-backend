package com.theron.wallet.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Component
public class JwtTokenProvider {

    public static final String CLAIM_PRINCIPAL = "principal";
    public static final String CLAIM_TYP = "typ";
    public static final String CLAIM_SID = "sid";
    public static final String CLAIM_ROLE = "role";
    public static final String TYP_ACCESS = "access";

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration-ms:86400000}")
    private long jwtExpirationMs;

    @Value("${jwt.access-expiration-ms:900000}")
    private long accessExpirationMs;

    public String generateToken(String email, String role) {
        SecretKey key = getSigningKey();
        return Jwts.builder()
                .subject(email)
                .claim(CLAIM_ROLE, role)
                .claim(CLAIM_PRINCIPAL, UserPrincipal.PRINCIPAL_ADMIN)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtExpirationMs))
                .signWith(key)
                .compact();
    }

    public String generateAccessToken(UUID userId, UUID sessionId) {
        return generateAccessToken(userId, sessionId, new Date(System.currentTimeMillis() + accessExpirationMs));
    }

    public String generateAccessToken(UUID userId, UUID sessionId, Date expiration) {
        SecretKey key = getSigningKey();
        return Jwts.builder()
                .subject(userId.toString())
                .claim(CLAIM_TYP, TYP_ACCESS)
                .claim(CLAIM_SID, sessionId.toString())
                .claim(CLAIM_PRINCIPAL, UserPrincipal.PRINCIPAL_USER)
                .issuedAt(new Date())
                .expiration(expiration)
                .signWith(key)
                .compact();
    }

    public String getEmailFromToken(String token) {
        return parseClaims(token).getSubject();
    }

    public String getRoleFromToken(String token) {
        return parseClaims(token).get(CLAIM_ROLE, String.class);
    }

    public String getPrincipalType(Claims claims) {
        String principal = claims.get(CLAIM_PRINCIPAL, String.class);
        if (principal != null) {
            return principal;
        }
        if (claims.get(CLAIM_SID) != null) {
            return UserPrincipal.PRINCIPAL_USER;
        }
        return UserPrincipal.PRINCIPAL_ADMIN;
    }

    public UUID getSessionId(Claims claims) {
        String sid = claims.get(CLAIM_SID, String.class);
        return sid == null ? null : UUID.fromString(sid);
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(getSigningKey()).build().parseSignedClaims(token);
            return true;
        } catch (Exception ex) {
            log.debug("Invalid JWT token");
            return false;
        }
    }

    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isExpired(Exception ex) {
        return ex instanceof ExpiredJwtException;
    }

    public long getExpirationMs() {
        return jwtExpirationMs;
    }

    public long getAccessExpirationMs() {
        return accessExpirationMs;
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
