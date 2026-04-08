package com.theron.wallet.service.impl;

import com.theron.wallet.dto.request.LoginRequest;
import com.theron.wallet.dto.response.LoginResponse;
import com.theron.wallet.entity.AdminUser;
import com.theron.wallet.exception.ResourceNotFoundException;
import com.theron.wallet.repository.AdminUserRepository;
import com.theron.wallet.security.JwtTokenProvider;
import com.theron.wallet.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AdminUserRepository adminUserRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public LoginResponse login(LoginRequest request) {
        log.info("Login attempt: email={}", request.getEmail());

        AdminUser admin = adminUserRepository.findByEmailAndActiveTrue(request.getEmail())
                .orElseThrow(() -> {
                    log.warn("Login failed — user not found: email={}", request.getEmail());
                    return new ResourceNotFoundException("Invalid email or password");
                });

        if (!passwordEncoder.matches(request.getPassword(), admin.getPasswordHash())) {
            log.warn("Login failed — wrong password: email={}", request.getEmail());
            throw new ResourceNotFoundException("Invalid email or password");
        }

        String token = jwtTokenProvider.generateToken(admin.getEmail(), admin.getRole());
        log.info("Login successful: email={}, adminId={}", admin.getEmail(), admin.getId());

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
}

