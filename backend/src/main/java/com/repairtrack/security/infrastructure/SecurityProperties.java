package com.repairtrack.security.infrastructure;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Token settings, bound from {@code repairtrack.security.*}. Validated at startup so a weak or
 * missing secret stops the application instead of producing forgeable tokens.
 */
@ConfigurationProperties(prefix = "repairtrack.security")
public record SecurityProperties(Jwt jwt, Duration refreshTokenTtl) {

    /** HS256 needs a key of at least 256 bits. */
    static final int MIN_SECRET_BYTES = 32;

    public SecurityProperties {
        Objects.requireNonNull(jwt, "repairtrack.security.jwt must be configured");
        requirePositive(refreshTokenTtl, "repairtrack.security.refresh-token-ttl");
    }

    public record Jwt(String secret, String issuer, Duration accessTokenTtl) {

        public Jwt {
            if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
                throw new IllegalStateException(
                        "repairtrack.security.jwt.secret (JWT_SECRET) must be at least " + MIN_SECRET_BYTES + " bytes");
            }
            if (issuer == null || issuer.isBlank()) {
                throw new IllegalStateException("repairtrack.security.jwt.issuer must be configured");
            }
            requirePositive(accessTokenTtl, "repairtrack.security.jwt.access-token-ttl");
        }

        @Override
        public String toString() {
            // never print the secret (e.g. in startup failure reports)
            return "Jwt[issuer=" + issuer + ", accessTokenTtl=" + accessTokenTtl + "]";
        }
    }

    private static void requirePositive(Duration duration, String property) {
        if (duration == null || duration.isNegative() || duration.isZero()) {
            throw new IllegalStateException(property + " must be a positive duration");
        }
    }
}
