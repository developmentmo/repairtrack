package com.repairtrack.security;

import java.time.Instant;
import java.util.UUID;

/** Account changes, for the audit trail. IDs only, no personal data, never tokens. */
public final class UserAccountEvents {

    private UserAccountEvents() {
    }

    public record EmailVerified(UUID userId, Instant occurredAt) {
    }

    public record PasswordReset(UUID userId, Instant occurredAt) {
    }

    public record UserBlocked(UUID userId, UUID blockedBy, Instant occurredAt) {
    }

    public record UserUnblocked(UUID userId, UUID unblockedBy, Instant occurredAt) {
    }

    /**
     * The user deleted their own account. Published inside the deleting transaction: listeners end what the user
     * still holds (vehicle ownerships, garage memberships) and may veto the deletion by throwing.
     */
    public record AccountDeleted(UUID userId, Instant occurredAt) {
    }
}
