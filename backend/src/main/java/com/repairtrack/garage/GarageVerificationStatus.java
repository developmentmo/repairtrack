package com.repairtrack.garage;

/**
 * Whether RepairTrack has verified that a garage is a real, registered business.
 * Determines the source type of records a garage creates (GARAGE vs VERIFIED_GARAGE).
 */
public enum GarageVerificationStatus {

    /** Reviewed and not (or no longer) verified. Can re-apply. */
    UNVERIFIED,

    /** Awaiting review by a system admin. Initial status of a new garage. */
    PENDING,

    /** Verified by a system admin. */
    VERIFIED,

    /** Temporarily barred: cannot record work until reinstated. */
    SUSPENDED
}
