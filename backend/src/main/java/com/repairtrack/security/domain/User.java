package com.repairtrack.security.domain;

import java.time.Instant;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import com.repairtrack.security.Role;

/**
 * A person with an account. Never physically deleted: status {@link UserStatus#DELETED} is used
 * so that historical records (repairs, audit) keep a valid author reference.
 */
@Entity
@Table(name = "app_user")
public class User {

    @Id
    private UUID id;

    @Column(name = "email", nullable = false, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UserStatus status;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "app_user_role", joinColumns = @JoinColumn(name = "user_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 30)
    private Set<Role> roles = new HashSet<>();

    /** Null until the user confirmed their email address; login is refused until then. */
    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Optimistic locking; a null version also tells Spring Data the entity is new. */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected User() {
        // for JPA
    }

    /**
     * Creates a new active account. Every self-registered user gets exactly {@link Role#OWNER};
     * roles are never taken from client input.
     */
    public static User register(String email, String passwordHash, String firstName, String lastName, Instant now) {
        User user = new User();
        user.id = UUID.randomUUID();
        user.email = normalizeEmail(email);
        user.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
        user.firstName = requireText(firstName, "firstName");
        user.lastName = requireText(lastName, "lastName");
        user.status = UserStatus.ACTIVE;
        user.roles = new HashSet<>(EnumSet.of(Role.OWNER));
        user.createdAt = Objects.requireNonNull(now, "now");
        user.updatedAt = now;
        return user;
    }

    /** Canonical form used for storage and lookup: trimmed, lower-case (locale-independent). */
    public static String normalizeEmail(String email) {
        return requireText(email, "email").trim().toLowerCase(Locale.ROOT);
    }

    public void block(Instant now) {
        if (status == UserStatus.DELETED) {
            throw new IllegalStateException("A deleted user cannot be blocked");
        }
        status = UserStatus.BLOCKED;
        updatedAt = now;
    }

    public void unblock(Instant now) {
        if (status != UserStatus.BLOCKED) {
            throw new IllegalStateException("Only a blocked user can be unblocked");
        }
        status = UserStatus.ACTIVE;
        updatedAt = now;
    }

    /** Idempotent: the first confirmation counts. */
    public void verifyEmail(Instant now) {
        if (emailVerifiedAt == null) {
            emailVerifiedAt = Objects.requireNonNull(now, "now");
            updatedAt = now;
        }
    }

    public void changePasswordHash(String newPasswordHash, Instant now) {
        passwordHash = Objects.requireNonNull(newPasswordHash, "passwordHash");
        updatedAt = now;
    }

    public boolean isEmailVerified() {
        return emailVerifiedAt != null;
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public UserStatus getStatus() {
        return status;
    }

    public Set<Role> getRoles() {
        return Set.copyOf(roles);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
