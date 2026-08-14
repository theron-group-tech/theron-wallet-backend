package com.theron.wallet.security;

import jakarta.servlet.http.HttpServletRequest;

public final class ClientRequestMetadata {

    private ClientRequestMetadata() {
    }

    public static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip;
        if (forwarded != null && !forwarded.isBlank()) {
            ip = forwarded.split(",")[0].trim();
        } else {
            ip = request.getRemoteAddr();
        }
        return truncate(ip, 45);
    }

    public static String userAgent(HttpServletRequest request) {
        return truncate(request.getHeader("User-Agent"), 512);
    }

    private static String truncate(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}
