package com.repairtrack.security.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Server-side record of an opaque refresh token. Only the SHA-256 hash of the token is stored.
 * <p>
 * Tokens are single-use and rotated: every refresh revokes the presented token and issues a new
 * one in the same {@code familyId} (one family per login). Presenting an already-revoked token
 * means it was stolen or replayed, so the whole family is revoked.
 */
@Entity
@Table(name = "refresh_token")
public class RefreshToken {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "family_id", nullable = false, updatable = false)
    private UUID familyId;

    @Column(name = "token_hash", nullable = false, updatable = false, length = 64)
    private String tokenHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "replaced_by_id")
    private UUID replacedById;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected RefreshToken() {
        // for JPA
    }

    public static RefreshToken issue(UUID userId, UUID familyId, String tokenHash, Instant now, Duration ttl) {
        RefreshToken token = new RefreshToken();
        token.id = UUID.randomUUID();
        token.userId = Objects.requireNonNull(userId, "userId");
        token.familyId = Objects.requireNonNull(familyId, "familyId");
        token.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash");
        token.createdAt = Objects.requireNonNull(now, "now");
        token.expiresAt = now.plus(ttl);
        return token;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public void revoke(Instant now) {
        if (revokedAt == null) {
            revokedAt = now;
        }
    }

    /** Revokes this token as part of a rotation and links it to its successor. */
    public void rotateTo(RefreshToken successor, Instant now) {
        if (!successor.familyId.equals(familyId)) {
            throw new IllegalArgumentException("Successor must belong to the same token family");
        }
        revoke(now);
        replacedById = successor.id;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getFamilyId() {
        return familyId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public UUID getReplacedById() {
        return replacedById;
    }
}
