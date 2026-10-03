package com.repairtrack.security.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * A single-use token sent by email (verify the address, reset the password). Only the SHA-256 hash of the
 * token is stored; the token itself exists only in the email.
 */
@Entity
@Table(name = "account_token")
public class AccountToken {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, updatable = false, length = 30)
    private AccountTokenPurpose purpose;

    @Column(name = "token_hash", nullable = false, updatable = false, length = 64)
    private String tokenHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected AccountToken() {
        // for JPA
    }

    public static AccountToken issue(UUID userId, AccountTokenPurpose purpose, String tokenHash, Instant now) {
        AccountToken token = new AccountToken();
        token.id = UUID.randomUUID();
        token.userId = Objects.requireNonNull(userId, "userId");
        token.purpose = Objects.requireNonNull(purpose, "purpose");
        token.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash");
        token.createdAt = Objects.requireNonNull(now, "now");
        token.expiresAt = now.plus(purpose.validity());
        return token;
    }

    public boolean isUsableFor(AccountTokenPurpose expected, Instant now) {
        return purpose == expected && usedAt == null && now.isBefore(expiresAt);
    }

    public void use(Instant now) {
        if (usedAt != null) {
            throw new IllegalStateException("Account token already used");
        }
        usedAt = Objects.requireNonNull(now, "now");
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public AccountTokenPurpose getPurpose() {
        return purpose;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }
}
