package com.repairtrack.vehicle.domain;

public enum VehicleStatus {
    ACTIVE,
    /** No longer on the road (scrapped, exported). History stays available. */
    ARCHIVED
}
