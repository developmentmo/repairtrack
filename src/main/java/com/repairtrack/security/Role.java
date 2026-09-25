package com.repairtrack.security;

/**
 * Platform-wide roles of a user account.
 * <p>
 * Garage roles ({@code GARAGE_ADMIN}, {@code MECHANIC}) are deliberately NOT here: they are
 * scoped to one garage and live on the garage membership (GarageUser, Phase 3). A person can be
 * a mechanic at one garage and simply a car owner everywhere else; a global role cannot express that.
 */
public enum Role {

    /** Every registered user. Can own vehicles and manage their own data. */
    OWNER,

    /** RepairTrack operators (e.g. verifying garages). Never assignable through the public API. */
    SYSTEM_ADMIN
}
