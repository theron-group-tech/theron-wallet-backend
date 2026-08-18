package com.theron.wallet.security;

import com.theron.wallet.entity.AdminUser;
import com.theron.wallet.repository.AdminUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultAdminSeeder implements ApplicationRunner {

    private final AdminUserRepository adminUserRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    @Value("${app.admin.email:}")
    private String defaultAdminEmail;

    @Value("${app.admin.password:}")
    private String defaultAdminPassword;

    @Override
    public void run(ApplicationArguments args) {
        if (adminUserRepository.count() > 0) {
            return;
        }
        if (defaultAdminEmail == null || defaultAdminEmail.isBlank()
                || defaultAdminPassword == null || defaultAdminPassword.isBlank()) {
            log.warn("No admin users exist and app.admin.email/password are not set; skipping seed");
            return;
        }
        if (SecuritySecretsValidator.FORBIDDEN_SECRETS.contains(defaultAdminPassword)) {
            throw new IllegalStateException(
                    "Refusing to seed admin with a known default password. Set DEFAULT_ADMIN_PASSWORD.");
        }
        AdminUser admin = AdminUser.builder()
                .name("Theron Admin")
                .email(defaultAdminEmail.trim().toLowerCase())
                .passwordHash(passwordEncoder.encode(defaultAdminPassword))
                .role("ADMIN")
                .active(true)
                .build();
        adminUserRepository.save(admin);
        log.info("Default admin created: email={}", admin.getEmail());
    }
}
