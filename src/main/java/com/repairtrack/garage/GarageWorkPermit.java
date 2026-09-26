package com.repairtrack.garage;

import java.util.UUID;

/**
 * Proof that a user may act on behalf of a garage (register a customer's vehicle, record
 * repairs and maintenance), as of the
 * moment it was issued. Returned by {@link GarageAccessService#validateCanRecordWork}.
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
