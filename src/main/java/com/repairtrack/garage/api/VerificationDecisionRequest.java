package com.repairtrack.garage.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.repairtrack.garage.GarageVerificationStatus;

/** System-admin decision on a garage's verification. Allowed transitions are enforced by the domain. */
public record VerificationDecisionRequest(
        @NotNull GarageVerificationStatus status,
        @Size(max = 500) String note
) {
}
