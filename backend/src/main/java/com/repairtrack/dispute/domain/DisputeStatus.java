package com.repairtrack.dispute.domain;

import java.util.EnumSet;
import java.util.Set;

public enum DisputeStatus {
    /** Waiting for the current owner's response (until the response deadline). */
    OPEN,
    /** The owner responded, or the deadline passed: a system admin can decide. */
    AWAITING_REVIEW,
    /** The claimant is the rightful owner; the contested ownership was revoked. */
    UPHELD,
    /** The current owner stays the owner. */
    REJECTED;

    public static final Set<DisputeStatus> UNDECIDED = EnumSet.of(OPEN, AWAITING_REVIEW);

    public boolean isDecided() {
        return this == UPHELD || this == REJECTED;
    }
}
