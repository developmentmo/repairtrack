package com.repairtrack.garage;

import java.time.Instant;
import java.util.UUID;

/**
 * Events published by the garage module. Consumers: audit (Phase 5), notifications.
 * They carry IDs only, no personal data.
 */
public final class GarageEvents {

    private GarageEvents() {
    }

    public record GarageRegistered(UUID garageId, UUID registeredBy, Instant occurredAt) {
    }

    public record GarageVerificationStatusChanged(
            UUID garageId,
            GarageVerificationStatus from,
            GarageVerificationStatus to,
            UUID changedBy,
            String note,
            Instant occurredAt) {
    }

    public record GarageMemberAdded(UUID garageId, UUID userId, GarageRole role, UUID addedBy, Instant occurredAt) {
    }

    public record GarageMemberRemoved(UUID garageId, UUID userId, UUID removedBy, Instant occurredAt) {
    }
}
