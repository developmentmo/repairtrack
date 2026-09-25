package com.repairtrack.garage;

/** Role of a user within ONE garage (see GarageUser). Not a platform-wide role. */
public enum GarageRole {

    /** Manages the garage profile and its members; can also record work. */
    GARAGE_ADMIN,

    /** Records maintenance and repairs on behalf of the garage. */
    MECHANIC
}
