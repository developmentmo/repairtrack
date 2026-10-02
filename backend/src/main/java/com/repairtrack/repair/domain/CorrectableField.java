package com.repairtrack.repair.domain;

/**
 * Fields that may be corrected. Not correctable: vehicle (record on the wrong vehicle -> void
 * and re-create), garage, source type and verification status (decided by the backend).
 */
public enum CorrectableField {
    EVENT_TYPE,
    EVENT_DATE,
    MILEAGE,
    TITLE,
    DESCRIPTION
}
