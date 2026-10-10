package com.repairtrack.security.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class LoginSessionTest {

    private static final Instant NOW = Instant.parse("2026-10-10T10:00:00Z");
    private static final Duration IDLE_TIMEOUT = Duration.ofMinutes(15);

    @Test
    void isActiveUntilTheIdleTimeoutHasPassed() {
        LoginSession session = LoginSession.start(UUID.randomUUID(), NOW);

        assertThat(session.isActive(NOW.plus(IDLE_TIMEOUT).minusSeconds(1), IDLE_TIMEOUT)).isTrue();
        assertThat(session.isActive(NOW.plus(IDLE_TIMEOUT), IDLE_TIMEOUT)).isFalse();
    }

    @Test
    void activityExtendsTheSession() {
        LoginSession session = LoginSession.start(UUID.randomUUID(), NOW);

        session.recordActivity(NOW.plus(Duration.ofMinutes(10)));

        assertThat(session.isActive(NOW.plus(Duration.ofMinutes(24)), IDLE_TIMEOUT)).isTrue();
        assertThat(session.isActive(NOW.plus(Duration.ofMinutes(25)), IDLE_TIMEOUT)).isFalse();
    }

    @Test
    void activityNeverMovesBackInTime() {
        LoginSession session = LoginSession.start(UUID.randomUUID(), NOW);

        session.recordActivity(NOW.minusSeconds(60));

        assertThat(session.getLastActivityAt()).isEqualTo(NOW);
    }

    @Test
    void anEndedSessionIsNeverActiveAgain() {
        LoginSession session = LoginSession.start(UUID.randomUUID(), NOW);

        session.end(NOW.plusSeconds(1));
        session.recordActivity(NOW.plusSeconds(2));

        assertThat(session.isActive(NOW.plusSeconds(3), IDLE_TIMEOUT)).isFalse();
        assertThat(session.getEndedAt()).isEqualTo(NOW.plusSeconds(1));
    }

    @Test
    void activityIsOnlyWorthWritingAfterTheInterval() {
        LoginSession session = LoginSession.start(UUID.randomUUID(), NOW);

        assertThat(session.isActivityOlderThan(Duration.ofMinutes(1), NOW.plusSeconds(60))).isFalse();
        assertThat(session.isActivityOlderThan(Duration.ofMinutes(1), NOW.plusSeconds(61))).isTrue();
    }
}
