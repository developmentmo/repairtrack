package com.repairtrack.garage;

import java.util.UUID;

/**
 * Proof that a user may record work (repairs, maintenance) on behalf of a garage, as of the
 * moment it was issued. Returned by {@link GarageAccessService#validateCanCreateRepair}.
 * The repair module uses {@link #garageVerified()} to decide between source types
 * GARAGE and VERIFIED_GARAGE.
 */
public record GarageWorkPermit(
        UUID garageId,
        UUID userId,
        GarageRole role,
        GarageVerificationStatus garageVerificationStatus
) {

    public boolean garageVerified() {
        return garageVerificationStatus == GarageVerificationStatus.VERIFIED;
    }
}
