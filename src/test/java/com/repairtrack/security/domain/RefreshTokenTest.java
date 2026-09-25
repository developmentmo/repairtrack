package com.repairtrack.security.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class RefreshTokenTest {

    private static final Instant NOW = Instant.parse("2026-09-25T10:00:00Z");
    private static final Duration TTL = Duration.ofDays(30);

    @Test
    void newTokenIsActiveUntilExpiry() {
        RefreshToken token = RefreshToken.issue(UUID.randomUUID(), UUID.randomUUID(), "hash", NOW, TTL);

        assertThat(token.isRevoked()).isFalse();
        assertThat(token.isExpired(NOW)).isFalse();
        assertThat(token.isExpired(NOW.plus(TTL).minusMillis(1))).isFalse();
        assertThat(token.isExpired(NOW.plus(TTL))).isTrue();
    }

    @Test
    void rotationRevokesAndLinksSuccessor() {
        UUID family = UUID.randomUUID();
        RefreshToken current = RefreshToken.issue(UUID.randomUUID(), family, "h1", NOW, TTL);
        RefreshToken successor = RefreshToken.issue(current.getUserId(), family, "h2", NOW, TTL);

        current.rotateTo(successor, NOW.plusSeconds(5));

        assertThat(current.isRevoked()).isTrue();
        assertThat(current.getRevokedAt()).isEqualTo(NOW.plusSeconds(5));
        assertThat(current.getReplacedById()).isEqualTo(successor.getId());
    }

    @Test
    void rotationAcrossFamiliesIsRejected() {
        RefreshToken current = RefreshToken.issue(UUID.randomUUID(), UUID.randomUUID(), "h1", NOW, TTL);
        RefreshToken other = RefreshToken.issue(current.getUserId(), UUID.randomUUID(), "h2", NOW, TTL);

        assertThatThrownBy(() -> current.rotateTo(other, NOW)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void revokeKeepsFirstRevocationTime() {
        RefreshToken token = RefreshToken.issue(UUID.randomUUID(), UUID.randomUUID(), "h", NOW, TTL);

        token.revoke(NOW.plusSeconds(1));
        token.revoke(NOW.plusSeconds(99));

        assertThat(token.getRevokedAt()).isEqualTo(NOW.plusSeconds(1));
    }
}
