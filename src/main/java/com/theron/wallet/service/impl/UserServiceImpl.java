package com.theron.wallet.service.impl;

import com.theron.wallet.dto.request.CreateUserRequest;
import com.theron.wallet.dto.request.UpdateUserRequest;
import com.theron.wallet.dto.response.UserOrganizationResponse;
import com.theron.wallet.dto.response.UserResponse;
import com.theron.wallet.entity.User;
import com.theron.wallet.enums.AuditAction;
import com.theron.wallet.enums.NotificationType;
import com.theron.wallet.enums.UserStatus;
import com.theron.wallet.exception.DuplicateResourceException;
import com.theron.wallet.exception.InvalidRequestException;
import com.theron.wallet.exception.ResourceNotFoundException;
import com.theron.wallet.mapper.UserMapper;
import com.theron.wallet.repository.OrganizationMembershipRepository;
import com.theron.wallet.repository.UserRepository;
import com.theron.wallet.service.AuditLogService;
import com.theron.wallet.service.NotificationService;
import com.theron.wallet.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;

    @Override
    @Transactional
    public UserResponse create(CreateUserRequest request) {
        String email = normalizeEmail(request.getEmail());
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("User", "email", email);
        }

        User user = User.builder()
                .name(request.getName().trim())
                .email(email)
                .phone(trimToNull(request.getPhone()))
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .status(UserStatus.ACTIVE)
                .build();

        user = userRepository.save(user);
        log.info("User created: userId={}", user.getId());
        auditLogService.record(
                AuditAction.USER_CREATED, null, user.getId(), "User", user.getId(), Map.of("email", email));
        return UserMapper.toResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse findById(UUID id) {
        return UserMapper.toResponse(getUserOrThrow(id));
    }

    @Override
    @Transactional
    public UserResponse update(UUID id, UpdateUserRequest request) {
        User user = getUserOrThrow(id);
        UserStatus previousStatus = user.getStatus();
        boolean passwordChanged = false;

        if (request.getName() != null) {
            String name = request.getName().trim();
            if (name.isEmpty()) {
                throw new InvalidRequestException("name must not be blank");
            }
            user.setName(name);
        }
        if (request.getPhone() != null) {
            user.setPhone(trimToNull(request.getPhone()));
        }
        if (request.getStatus() != null) {
            user.setStatus(request.getStatus());
        }
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
            passwordChanged = true;
        }

        user = userRepository.save(user);
        log.info("User updated: userId={}", user.getId());
        if (passwordChanged) {
            auditLogService.record(AuditAction.PASSWORD_CHANGED, null, user.getId(), "User", user.getId(), null);
            notificationService.notify(user.getId(), null, NotificationType.PASSWORD_CHANGED, user.getId(), null);
        }
        if (previousStatus != UserStatus.SUSPENDED && user.getStatus() == UserStatus.SUSPENDED) {
            auditLogService.record(
                    AuditAction.USER_DISABLED, null, user.getId(), "User", user.getId(), Map.of("status", "SUSPENDED"));
        }
        return UserMapper.toResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserOrganizationResponse> listOrganizations(UUID userId) {
        getUserOrThrow(userId);
        return membershipRepository.findByUserId(userId).stream()
                .map(UserMapper::toUserOrganizationResponse)
                .toList();
    }

    private User getUserOrThrow(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
