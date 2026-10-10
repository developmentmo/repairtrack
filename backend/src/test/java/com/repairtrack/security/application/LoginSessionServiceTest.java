package com.repairtrack.security.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.repairtrack.security.domain.LoginSession;
import com.repairtrack.security.infrastructure.LoginSessionRepository;
import com.repairtrack.security.infrastructure.SecurityProperties;

@ExtendWith(MockitoExtension.class)
class LoginSessionServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-10T10:00:00Z");
    private static final UUID USER = UUID.randomUUID();

    @Mock
    private LoginSessionRepository sessions;

    private LoginSessionService service;

    @BeforeEach
    void setUp() {
        var properties = new SecurityProperties(
                new SecurityProperties.Jwt("0123456789abcdef0123456789abcdef", "repairtrack", Duration.ofMinutes(15)),
                Duration.ofDays(30), null);
        service = new LoginSessionService(sessions, properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void theIdleTimeoutDefaultsToFifteenMinutes() {
        LoginSession fourteenMinutes = stored(LoginSession.start(USER, NOW.minus(Duration.ofMinutes(14))));
        LoginSession fifteenMinutes = stored(LoginSession.start(USER, NOW.minus(Duration.ofMinutes(15))));

        assertThat(service.recordActivity(fourteenMinutes.getId(), USER)).isTrue();
        assertThat(service.recordActivity(fifteenMinutes.getId(), USER)).isFalse();
    }

    @Test
    void activityIsRecordedAtMostOncePerInterval() {
        LoginSession recent = stored(LoginSession.start(USER, NOW.minusSeconds(30)));
        LoginSession older = stored(LoginSession.start(USER, NOW.minus(Duration.ofMinutes(5))));

        service.recordActivity(recent.getId(), USER);
        service.recordActivity(older.getId(), USER);

        assertThat(recent.getLastActivityAt()).isEqualTo(NOW.minusSeconds(30));
        assertThat(older.getLastActivityAt()).isEqualTo(NOW);
    }

    @Test
    void anotherUsersSessionIsRejected() {
        LoginSession session = stored(LoginSession.start(USER, NOW));

        assertThat(service.recordActivity(session.getId(), UUID.randomUUID())).isFalse();
    }

    @Test
    void anUnknownSessionIsRejected() {
        UUID unknown = UUID.randomUUID();
        when(sessions.findById(unknown)).thenReturn(Optional.empty());

        assertThat(service.recordActivity(unknown, USER)).isFalse();
    }

    @Test
    void anEndedSessionIsRejected() {
        LoginSession session = stored(LoginSession.start(USER, NOW));
        session.end(NOW);

        assertThat(service.recordActivity(session.getId(), USER)).isFalse();
    }

    private LoginSession stored(LoginSession session) {
        when(sessions.findById(session.getId())).thenReturn(Optional.of(session));
        return session;
    }
}
