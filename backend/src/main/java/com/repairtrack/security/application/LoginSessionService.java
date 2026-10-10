package com.repairtrack.security.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.repairtrack.security.domain.LoginSession;
import com.repairtrack.security.infrastructure.LoginSessionRepository;
import com.repairtrack.security.infrastructure.SecurityProperties;

/**
 * Login sessions with an idle timeout ({@code repairtrack.security.session-idle-timeout}, default 15 minutes).
 * Checked on every authenticated request and on every refresh, so an idle session ends even while its access token
 * has not expired yet.
 */
@Service
public class LoginSessionService {

    /**
     * Activity is written at most this often per session, so most requests only read. The idle timeout is therefore
     * exact to within this interval.
     */
    static final Duration ACTIVITY_WRITE_INTERVAL = Duration.ofMinutes(1);

    private final LoginSessionRepository sessions;
    private final SecurityProperties properties;
    private final Clock clock;

    public LoginSessionService(LoginSessionRepository sessions, SecurityProperties properties, Clock clock) {
        this.sessions = sessions;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public LoginSession start(UUID userId, Instant now) {
        return sessions.save(LoginSession.start(userId, now));
    }

    /**
     * Records activity in the session of {@code userId}. Returns false when the session does not exist, belongs to
     * someone else, was ended or has been idle for longer than the timeout.
     */
    @Transactional
    public boolean recordActivity(UUID sessionId, UUID userId) {
        Instant now = Instant.now(clock);
        LoginSession session = sessions.findById(sessionId).orElse(null);
        if (session == null || !session.getUserId().equals(userId)
                || !session.isActive(now, properties.sessionIdleTimeout())) {
            return false;
        }
        if (session.isActivityOlderThan(ACTIVITY_WRITE_INTERVAL, now)) {
            session.recordActivity(now);
        }
        return true;
    }

    @Transactional
    public void end(UUID sessionId, Instant now) {
        sessions.end(sessionId, now);
    }

    @Transactional
    public void endAllOfUser(UUID userId, Instant now) {
        sessions.endAllOfUser(userId, now);
    }
}
