package com.theron.wallet.security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@Profile("!test")
@Order(0)
@RequiredArgsConstructor
public class SecuritySecretsValidator implements ApplicationRunner {

    static final Set<String> FORBIDDEN_SECRETS = Set.of(
            "theronGroupWalletServiceSecretKey2024MustBe256BitsLong!",
            "nIlpBopPKbJrzQqLsQcY/EItC724S8pmH2ZI/bTRQrE=",
            "62862055"
    );

    @Value("${jwt.secret:}")
    private String jwtSecret;

    @Value("${encryption.aes-key:}")
    private String aesKey;

    @Value("${app.admin.password:}")
    private String adminPassword;

    @Override
    public void run(ApplicationArguments args) {
        rejectIfMissingOrKnown("jwt.secret", jwtSecret);
        rejectIfMissingOrKnown("encryption.aes-key", aesKey);
        if (adminPassword != null && !adminPassword.isBlank() && FORBIDDEN_SECRETS.contains(adminPassword)) {
            throw new IllegalStateException(
                    "Refusing to start: app.admin.password matches a known default. Set DEFAULT_ADMIN_PASSWORD.");
        }
    }

    private static void rejectIfMissingOrKnown(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Refusing to start: " + name + " is not set.");
        }
        if (FORBIDDEN_SECRETS.contains(value)) {
            throw new IllegalStateException(
                    "Refusing to start: " + name + " matches a known default committed to git. Rotate it.");
        }
    }
}
