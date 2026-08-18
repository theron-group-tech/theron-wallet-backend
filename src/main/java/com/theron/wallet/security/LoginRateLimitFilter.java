package com.theron.wallet.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.theron.wallet.exception.ApiErrorCodes;
import com.theron.wallet.exception.ApiErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@RequiredArgsConstructor
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private static final String LOGIN_PATH = "/api/v1/auth/login";
    private static final String REFRESH_PATH = "/api/v1/auth/refresh";

    private final ObjectMapper objectMapper;

    @Value("${theron.security.login-max-attempts:20}")
    private int maxAttempts;

    @Value("${theron.security.login-window-seconds:300}")
    private int windowSeconds;

    private final ConcurrentHashMap<String, Window> attempts = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (!isProtected(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        String key = clientKey(request);
        if (isLimited(key)) {
            writeTooManyRequests(request, response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    public void setMaxAttemptsForTests(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public void clearAttempts() {
        attempts.clear();
    }

    private boolean isLimited(String key) {
        long now = System.currentTimeMillis();
        long windowMs = windowSeconds * 1000L;
        Window window = attempts.compute(key, (ignored, existing) -> {
            if (existing == null || now - existing.startedAtMs >= windowMs) {
                return new Window(now);
            }
            existing.count.incrementAndGet();
            return existing;
        });
        return window.count.get() > maxAttempts;
    }

    private static boolean isProtected(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI();
        return LOGIN_PATH.equals(path) || REFRESH_PATH.equals(path);
    }

    private static String clientKey(HttpServletRequest request) {
        String ip = request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
        return request.getRequestURI() + "|" + ip;
    }

    private void writeTooManyRequests(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        ApiErrorResponse body = ApiErrorResponse.of(
                429,
                ApiErrorCodes.RATE_LIMITED,
                "Too Many Requests",
                "Too many authentication attempts. Try again later.",
                request.getRequestURI());
        response.setStatus(429);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), body);
    }

    private static final class Window {
        private final long startedAtMs;
        private final AtomicInteger count = new AtomicInteger(1);

        private Window(long startedAtMs) {
            this.startedAtMs = startedAtMs;
        }
    }
}
