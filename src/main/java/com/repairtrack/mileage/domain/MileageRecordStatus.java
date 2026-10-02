package com.repairtrack.mileage.domain;

public enum MileageRecordStatus {
    ACTIVE,
    /** Source was voided or corrected; kept for traceability, ignored in analysis. */
    VOIDED
}
