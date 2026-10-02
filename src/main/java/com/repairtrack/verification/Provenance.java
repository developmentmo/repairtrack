package com.repairtrack.verification;

import java.util.Objects;

/** Source and verification status of a record, as decided by {@link VerificationService}. */
public record Provenance(SourceType sourceType, VerificationStatus verificationStatus) {

    public Provenance {
        Objects.requireNonNull(sourceType, "sourceType");
        Objects.requireNonNull(verificationStatus, "verificationStatus");
    }
}
