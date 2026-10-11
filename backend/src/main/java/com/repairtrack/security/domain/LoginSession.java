package com.repairtrack.security.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One login, from password check until logout or idle timeout. Its id is the {@code familyId} of the login's
 * refresh tokens and the {@code sid} claim of its access tokens, so both stop working when the session is over.
 * <p>
 * Every authenticated request and every refresh counts as activity. A session without activity for longer than the
 * idle timeout is over and cannot be revived.
 */
@Entity
@Table(name = "login_session")
public class LoginSession {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_activity_at", nullable = false)
    private Instant lastActivityAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    protected LoginSession() {
        // for JPA
    }

    public static LoginSession start(UUID userId, Instant now) {
        LoginSession session = new LoginSession();
        session.id = UUID.randomUUID();
        session.userId = Objects.requireNonNull(userId, "userId");
        session.createdAt = Objects.requireNonNull(now, "now");
        session.lastActivityAt = now;
        return session;
    }

    public boolean isActive(Instant now, Duration idleTimeout) {
        return endedAt == null && now.isBefore(lastActivityAt.plus(idleTimeout));
    }

    /** True when the last recorded activity is older than {@code interval}, so it is worth writing a new one. */
    public boolean isActivityOlderThan(Duration interval, Instant now) {
        return lastActivityAt.plus(interval).isBefore(now);
    }

    public void recordActivity(Instant now) {
        if (now.isAfter(lastActivityAt)) {
            lastActivityAt = now;
        }
    }

    public void end(Instant now) {
        if (endedAt == null) {
            endedAt = now;
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public Instant getLastActivityAt() {
        return lastActivityAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }
}
