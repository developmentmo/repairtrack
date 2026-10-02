package com.repairtrack.verification;

/** Who a history record comes from. Always determined by the backend. */
public enum SourceType {

    /** Entered by the vehicle owner, no supporting document. */
    OWNER,

    /** Entered by the owner with a supporting document (Phase 6). */
    OWNER_DOCUMENT,

    /** Recorded by a garage that is not (yet) verified by RepairTrack. */
    GARAGE,

    /** Recorded by a garage verified by RepairTrack. */
    VERIFIED_GARAGE,

    /** Imported from a manufacturer (future integration). */
    MANUFACTURER,

    /** Imported from RDW (future integration). */
    RDW
}
