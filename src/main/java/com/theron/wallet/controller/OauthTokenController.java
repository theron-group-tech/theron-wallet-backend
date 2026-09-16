package com.theron.wallet.controller;

import com.theron.wallet.dto.response.OauthTokenResponse;
import com.theron.wallet.service.OauthTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@RestController
@RequestMapping("/api/v1/oauth")
@RequiredArgsConstructor
@Tag(name = "OAuth", description = "OAuth 2.0 Client Credentials for B2B machine-to-machine access")
public class OauthTokenController {

    private final OauthTokenService oauthTokenService;

    @PostMapping(value = "/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @Operation(summary = "Issue access token (client_credentials)")
    public ResponseEntity<OauthTokenResponse> token(
            @RequestParam(value = "grant_type", required = false) String grantType,
            @RequestParam(value = "client_id", required = false) String clientId,
            @RequestParam(value = "client_secret", required = false) String clientSecret,
            @org.springframework.web.bind.annotation.RequestHeader(
                    value = "Authorization", required = false) String authorization) {

        String resolvedClientId = clientId;
        String resolvedSecret = clientSecret;
        if ((!StringUtils.hasText(resolvedClientId) || !StringUtils.hasText(resolvedSecret))
                && StringUtils.hasText(authorization)
                && authorization.regionMatches(true, 0, "Basic ", 0, 6)) {
            String decoded = new String(
                    Base64.getDecoder().decode(authorization.substring(6).trim()),
                    StandardCharsets.UTF_8);
            int colon = decoded.indexOf(':');
            if (colon > 0) {
                resolvedClientId = decoded.substring(0, colon);
                resolvedSecret = decoded.substring(colon + 1);
            }
        }

        return ResponseEntity.ok(oauthTokenService.issueClientCredentialsToken(
                resolvedClientId, resolvedSecret, grantType));
    }
}
