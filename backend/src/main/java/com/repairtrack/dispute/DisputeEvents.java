package com.repairtrack.dispute;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Events of the dispute module (audit). IDs and hashes only: never statements or file names. */
public final class DisputeEvents {

    private DisputeEvents() {
    }

    public record DisputeOpened(UUID disputeId, UUID vehicleId, UUID claimantId, UUID ownerId, Instant occurredAt) {
    }

    public record DisputeResponded(UUID disputeId, UUID vehicleId, UUID ownerId, Instant occurredAt) {
    }

    /** {@code party}: "CLAIMANT" or "OWNER". */
    public record DisputeEvidenceAdded(UUID disputeId, UUID evidenceId, String party, UUID submittedBy,
                                       String mimeType, long fileSize, String sha256, Instant occurredAt) {
    }

    /** {@code newOwnerSince} only when upheld. */
    public record DisputeDecided(UUID disputeId, UUID vehicleId, boolean upheld, UUID decidedBy,
                                 LocalDate newOwnerSince, Instant occurredAt) {
    }
}
