package com.repairtrack.mileage;

import java.util.List;

/** All active readings of a vehicle in chronological order, plus the inconsistencies between them. */
public record MileageHistory(List<MileageReading> readings, List<MileageAnomaly> anomalies) {
}
