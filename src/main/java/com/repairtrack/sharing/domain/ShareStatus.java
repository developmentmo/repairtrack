package com.repairtrack.sharing.domain;

public enum ShareStatus {
    ACTIVE,
    EXPIRED,
    REVOKED,
    /** The person who created the link no longer owns the vehicle; the link stopped working. */
    OWNER_CHANGED
}
