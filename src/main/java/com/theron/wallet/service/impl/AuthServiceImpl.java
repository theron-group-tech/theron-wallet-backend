package com.theron.wallet.service.impl;

import com.theron.wallet.dto.request.LoginRequest;
import com.theron.wallet.dto.response.AdminMeResponse;
import com.theron.wallet.dto.response.AuthSessionResponse;
import com.theron.wallet.dto.response.LoginResponse;
import com.theron.wallet.entity.AdminUser;
import com.theron.wallet.entity.AuthSession;
import com.theron.wallet.entity.Device;
import com.theron.wallet.entity.User;
import com.theron.wallet.enums.UserStatus;
import com.theron.wallet.exception.ResourceNotFoundException;
import com.theron.wallet.exception.UnauthorizedException;
import com.theron.wallet.mapper.UserMapper;
import com.theron.wallet.repository.AdminUserRepository;
import com.theron.wallet.repository.AuthSessionRepository;
import com.theron.wallet.repository.DeviceRepository;
import com.theron.wallet.repository.UserRepository;
import com.theron.wallet.security.JwtTokenProvider;
import com.theron.wallet.security.RefreshTokenHasher;
import com.theron.wallet.security.UserPrincipal;
import com.theron.wallet.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final String INVALID_CREDENTIALS = "Invalid email or password";
    private static final String INVALID_REFRESH = "Invalid refresh token";

    private final AdminUserRepository adminUserRepository;
    private final UserRepository userRepository;
    private final DeviceRepository deviceRepository;
    private final AuthSessionRepository authSessionRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenHasher refreshTokenHasher;

    @Value("${jwt.refresh-expiration-days:30}")
    private long refreshExpirationDays;

    @Override
    @Transactional
    public LoginResponse login(LoginRequest request, String ip, String userAgent) {
        String email = request.getEmail() == null ? null : request.getEmail().trim().toLowerCase();
        log.info("Login attempt");

        User user = userRepository.findByEmail(email).orElse(null);
        if (user != null) {
            return loginProductUser(user, request, ip, userAgent);
        }
        return loginAdmin(email, request.getPassword());
    }

    @Override
    @Transactional(noRollbackFor = UnauthorizedException.class)
    public LoginResponse refresh(String refreshToken, String ip, String userAgent) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new UnauthorizedException(INVALID_REFRESH);
        }

        String hash = refreshTokenHasher.sha256Hex(refreshToken);
        AuthSession session = authSessionRepository.findByRefreshTokenHashForUpdate(hash)
                .orElseThrow(() -> new UnauthorizedException(INVALID_REFRESH));

        if (session.getReplacedAt() != null || session.getRevokedAt() != null) {
            log.warn("Refresh token reuse detected: sessionId={}, userId={}", session.getId(), session.getUser().getId());
            revokeAllSessions(session.getUser().getId());
            throw new UnauthorizedException(INVALID_REFRESH);
        }

        if (session.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new UnauthorizedException(INVALID_REFRESH);
        }

        User user = session.getUser();
        if (user.getStatus() != UserStatus.ACTIVE) {
            revokeAllSessions(user.getId());
            throw new UnauthorizedException(INVALID_REFRESH);
        }

        LocalDateTime now = LocalDateTime.now();
        session.setReplacedAt(now);
        session.setLastUsedAt(now);
        authSessionRepository.save(session);

        Device device = session.getDevice();
        touchDevice(device, ip, userAgent, null, null);

        String plainRefresh = refreshTokenHasher.generatePlainToken();
        AuthSession newSession = createSession(user, device, plainRefresh);
        log.info("Token refreshed: userId={}, sessionId={}", user.getId(), newSession.getId());
        return productLoginResponse(user, newSession, plainRefresh);
    }

    @Override
    @Transactional
    public void logout(UserPrincipal principal, String refreshToken) {
        if (principal != null && principal.isProductUser() && principal.getSessionId() != null) {
            authSessionRepository.findById(principal.getSessionId()).ifPresent(this::revokeSessionInternal);
            log.info("Logout: sessionId={}", principal.getSessionId());
            return;
        }
        if (refreshToken != null && !refreshToken.isBlank()) {
            String hash = refreshTokenHasher.sha256Hex(refreshToken);
            authSessionRepository.findByRefreshTokenHashForUpdate(hash)
                    .ifPresent(this::revokeSessionInternal);
            log.info("Logout via refresh token");
            return;
        }
        throw new UnauthorizedException("Authentication required");
    }

    @Override
    @Transactional
    public void logoutAll(UserPrincipal principal) {
        UUID userId = requireProductUserId(principal);
        revokeAllSessions(userId);
        log.info("Logout-all: userId={}", userId);
    }

    @Override
    @Transactional(readOnly = true)
    public Object me(UserPrincipal principal) {
        if (principal == null) {
            throw new UnauthorizedException("Authentication required");
        }
        if (principal.isProductUser()) {
            User user = userRepository.findById(principal.getUserId())
                    .orElseThrow(() -> new UnauthorizedException("Authentication required"));
            return UserMapper.toResponse(user);
        }
        AdminUser admin = adminUserRepository.findByEmailAndActiveTrue(principal.getEmail())
                .orElseThrow(() -> new UnauthorizedException("Authentication required"));
        return AdminMeResponse.builder()
                .adminId(admin.getId())
                .name(admin.getName())
                .email(admin.getEmail())
                .role(admin.getRole())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuthSessionResponse> listSessions(UserPrincipal principal) {
        UUID userId = requireProductUserId(principal);
        return authSessionRepository
                .findByUser_IdAndRevokedAtIsNullAndReplacedAtIsNullOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toSessionResponse)
                .toList();
    }

    @Override
    @Transactional
    public void revokeSession(UserPrincipal principal, UUID sessionId) {
        UUID userId = requireProductUserId(principal);
        AuthSession session = authSessionRepository.findByIdAndUser_Id(sessionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("AuthSession", "id", sessionId));
        revokeSessionInternal(session);
        maybeRevokeDevice(session.getDevice());
        log.info("Session revoked: sessionId={}, userId={}", sessionId, userId);
    }

    private LoginResponse loginProductUser(User user, LoginRequest request, String ip, String userAgent) {
        if (user.getStatus() != UserStatus.ACTIVE
                || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            log.warn("Product login failed: userId={}", user.getId());
            throw new UnauthorizedException(INVALID_CREDENTIALS);
        }

        Device device = upsertDevice(user, request, ip, userAgent);
        String plainRefresh = refreshTokenHasher.generatePlainToken();
        AuthSession session = createSession(user, device, plainRefresh);

        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        log.info("Product login successful: userId={}, sessionId={}", user.getId(), session.getId());
        return productLoginResponse(user, session, plainRefresh);
    }

    private LoginResponse loginAdmin(String email, String password) {
        AdminUser admin = adminUserRepository.findByEmailAndActiveTrue(email)
                .orElseThrow(() -> {
                    log.warn("Login failed — user not found");
                    return new ResourceNotFoundException(INVALID_CREDENTIALS);
                });

        if (!passwordEncoder.matches(password, admin.getPasswordHash())) {
            log.warn("Login failed — wrong password: adminId={}", admin.getId());
            throw new ResourceNotFoundException(INVALID_CREDENTIALS);
        }

        String token = jwtTokenProvider.generateToken(admin.getEmail(), admin.getRole());
        log.info("Admin login successful: adminId={}", admin.getId());

        return LoginResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getExpirationMs())
                .adminId(admin.getId())
                .name(admin.getName())
                .email(admin.getEmail())
                .role(admin.getRole())
                .build();
    }

    private Device upsertDevice(User user, LoginRequest request, String ip, String userAgent) {
        String rawDeviceId = request.getDeviceId() == null || request.getDeviceId().isBlank()
                ? UUID.randomUUID().toString()
                : request.getDeviceId().trim();
        final String clientDeviceId = rawDeviceId.length() > 128 ? rawDeviceId.substring(0, 128) : rawDeviceId;

        Device device = deviceRepository.findByUserIdAndClientDeviceId(user.getId(), clientDeviceId)
                .orElseGet(() -> Device.builder()
                        .user(user)
                        .clientDeviceId(clientDeviceId)
                        .build());

        touchDevice(device, ip, userAgent, request.getDeviceName(), request.getPlatform());
        device.setRevokedAt(null);
        return deviceRepository.save(device);
    }

    private void touchDevice(Device device, String ip, String userAgent, String deviceName, String platform) {
        LocalDateTime now = LocalDateTime.now();
        device.setLastSeenAt(now);
        if (ip != null) {
            device.setIp(ip);
        }
        if (userAgent != null) {
            device.setUserAgent(userAgent);
        }
        if (deviceName != null && !deviceName.isBlank()) {
            device.setDeviceName(truncate(deviceName.trim(), 255));
        }
        if (platform != null && !platform.isBlank()) {
            device.setPlatform(truncate(platform.trim(), 64));
        }
    }

    private AuthSession createSession(User user, Device device, String plainRefresh) {
        LocalDateTime now = LocalDateTime.now();
        AuthSession session = AuthSession.builder()
                .user(user)
                .device(device)
                .refreshTokenHash(refreshTokenHasher.sha256Hex(plainRefresh))
                .expiresAt(now.plusDays(refreshExpirationDays))
                .lastUsedAt(now)
                .build();
        return authSessionRepository.save(session);
    }

    private LoginResponse productLoginResponse(User user, AuthSession session, String plainRefresh) {
        String access = jwtTokenProvider.generateAccessToken(user.getId(), session.getId());
        return LoginResponse.builder()
                .token(access)
                .accessToken(access)
                .refreshToken(plainRefresh)
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getAccessExpirationMs())
                .userId(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .build();
    }

    private void revokeAllSessions(UUID userId) {
        LocalDateTime now = LocalDateTime.now();
        List<AuthSession> sessions = authSessionRepository.findByUser_IdAndRevokedAtIsNull(userId);
        for (AuthSession session : sessions) {
            session.setRevokedAt(now);
        }
        authSessionRepository.saveAll(sessions);
    }

    private void revokeSessionInternal(AuthSession session) {
        if (session.getRevokedAt() == null) {
            session.setRevokedAt(LocalDateTime.now());
            authSessionRepository.save(session);
        }
    }

    private void maybeRevokeDevice(Device device) {
        long remaining = authSessionRepository.countByDevice_IdAndRevokedAtIsNullAndReplacedAtIsNull(device.getId());
        if (remaining == 0) {
            device.setRevokedAt(LocalDateTime.now());
            deviceRepository.save(device);
        }
    }

    private AuthSessionResponse toSessionResponse(AuthSession session) {
        Device device = session.getDevice();
        return AuthSessionResponse.builder()
                .id(session.getId())
                .deviceId(device.getId())
                .deviceName(device.getDeviceName())
                .platform(device.getPlatform())
                .ip(device.getIp())
                .createdAt(session.getCreatedAt())
                .lastUsedAt(session.getLastUsedAt())
                .expiresAt(session.getExpiresAt())
                .build();
    }

    private static UUID requireProductUserId(UserPrincipal principal) {
        if (principal == null || !principal.isProductUser() || principal.getUserId() == null) {
            throw new UnauthorizedException("Authentication required");
        }
        return principal.getUserId();
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
