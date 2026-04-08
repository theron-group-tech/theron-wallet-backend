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

    @Value("${app.admin.email:alexsandro.nunes@proton.me}")
    private String defaultAdminEmail;

    @Value("${app.admin.password:62862055}")
    private String defaultAdminPassword;

    @Override
    public void run(ApplicationArguments args) {
        if (adminUserRepository.count() == 0) {
            AdminUser admin = AdminUser.builder()
                    .name("Theron Admin")
                    .email(defaultAdminEmail)
                    .passwordHash(passwordEncoder.encode(defaultAdminPassword))
                    .role("ADMIN")
                    .active(true)
                    .build();
            adminUserRepository.save(admin);
            log.info("Default admin created: email={}", defaultAdminEmail);
            log.warn("SECURITY: Change the default admin password via POST /api/v1/auth/change-password before going to production!");
        }
    }
}

