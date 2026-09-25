package com.repairtrack.security;

import java.time.Instant;
import java.util.UUID;

/**
 * Published after a user account has been created. Intended consumers: audit (Phase 5), notifications.
 * Carries no personal data beyond the ID.
 */
public record UserRegisteredEvent(UUID userId, Instant occurredAt) {
}
