package com.repairtrack.garage;

import java.util.UUID;

/** Public face of a garage on history records: who did the work, and is that garage verified. */
public record GarageSummary(UUID id, String name, String city, GarageVerificationStatus verificationStatus) {
}
