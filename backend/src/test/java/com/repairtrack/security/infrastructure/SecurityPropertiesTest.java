package com.repairtrack.security.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class SecurityPropertiesTest {

    private static final String STRONG_SECRET = "0123456789abcdef0123456789abcdef";

    @Test
    void rejectsSecretShorterThan256Bits() {
        assertThatThrownBy(() -> new SecurityProperties.Jwt("too-short", "repairtrack", Duration.ofMinutes(15)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 bytes");
    }

    @Test
    void rejectsMissingSecret() {
        assertThatThrownBy(() -> new SecurityProperties.Jwt(null, "repairtrack", Duration.ofMinutes(15)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsNonPositiveTtl() {
        var jwt = new SecurityProperties.Jwt(STRONG_SECRET, "repairtrack", Duration.ofMinutes(15));

        assertThatThrownBy(() -> new SecurityProperties(jwt, Duration.ZERO, null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void toStringNeverContainsSecret() {
        var jwt = new SecurityProperties.Jwt(STRONG_SECRET, "repairtrack", Duration.ofMinutes(15));

        assertThat(jwt.toString()).doesNotContain(STRONG_SECRET);
        assertThat(new SecurityProperties(jwt, Duration.ofDays(30), null).toString()).doesNotContain(STRONG_SECRET);
    }
}
