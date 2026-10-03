package com.repairtrack.vehicle.domain;

public enum OwnershipStatus {
    ACTIVE,
    /** Ended by the owner (sold, exported, scrapped). */
    ENDED,
    /**
     * Ended by a system admin after an upheld ownership dispute: the user was never the rightful owner. Kept in the
     * history (never deleted), but not counted as an owner and not a limit for the rightful owner's start date.
     */
    REVOKED
}
