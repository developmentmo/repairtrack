package com.repairtrack.repair.domain;

public enum RepairStatus {
    ACTIVE,
    /** Declared invalid (e.g. wrong vehicle). Stays visible in the history with its reason. */
    VOIDED
}
