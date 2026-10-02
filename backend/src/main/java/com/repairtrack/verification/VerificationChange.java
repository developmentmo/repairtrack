package com.repairtrack.verification;

import java.time.Instant;
import java.util.UUID;

/** A recorded change of a history record's provenance, with the evidence it is based on. */
public record VerificationChange(
        UUID id,
        UUID repairEventId,
        Provenance from,
        Provenance to,
        VerificationMethod method,
        UUID evidenceId,
        UUID changedBy,
        Instant changedAt
) {
}
