package com.repairtrack.garage.domain;

public enum MembershipStatus {
    ACTIVE,
    /** Membership ended (left or removed). Kept for history: past work stays attributable. */
    ENDED
}
