package com.theron.wallet.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenHasherTest {

    private final RefreshTokenHasher hasher = new RefreshTokenHasher();

    @Test
    void generatesUrlSafeTokenAndStableSha256() {
        String plain = hasher.generatePlainToken();
        assertThat(plain).isNotBlank().doesNotContain("=", "+", "/");
        assertThat(hasher.sha256Hex(plain)).hasSize(64).isEqualTo(hasher.sha256Hex(plain));
        assertThat(hasher.sha256Hex(plain)).isNotEqualTo(hasher.sha256Hex(hasher.generatePlainToken()));
    }
}
