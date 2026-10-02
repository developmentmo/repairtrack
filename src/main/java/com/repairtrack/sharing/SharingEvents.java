package com.repairtrack.sharing;

import java.time.Instant;
import java.util.UUID;

/** Events of the sharing module. Never contain the token or its hash. */
public final class SharingEvents {

    private SharingEvents() {
    }

    public record ShareCreated(UUID shareId, UUID vehicleId, UUID createdBy, Instant expiresAt,
                               boolean includeDocuments, Instant occurredAt) {
    }

    public record ShareRevoked(UUID shareId, UUID vehicleId, UUID revokedBy, Instant occurredAt) {
    }
}
