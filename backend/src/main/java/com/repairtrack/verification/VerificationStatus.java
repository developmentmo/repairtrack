package com.repairtrack.verification;

/** How much trust a history record deserves. Always determined by the backend. */
public enum VerificationStatus {
    UNVERIFIED,
    DOCUMENTED,
    GARAGE_VERIFIED,
    OFFICIAL_SOURCE
}
