package com.repairtrack.mileage;

/**
 * A reading that is lower than the reading before it (in date order).
 * <p>
 * Deliberately neutral: this is an inconsistency between registered values, not an accusation
 * of odometer fraud. Typos, a replaced instrument cluster or a wrongly dated entry are all
 * possible explanations.
 */
public record MileageAnomaly(String code, String message, MileageReading earlier, MileageReading later) {

    public static final String MILEAGE_DECREASE = "MILEAGE_DECREASE";

    public boolean involves(java.util.UUID readingId) {
        return earlier.id().equals(readingId) || later.id().equals(readingId);
    }
}
