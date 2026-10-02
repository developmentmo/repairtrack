package com.repairtrack.mileage;

import java.time.LocalDate;
import java.util.UUID;

import com.repairtrack.verification.SourceType;

/** One active odometer reading of a vehicle. {@code sourceEventId}: the repair event it came from, if any. */
public record MileageReading(UUID id, int mileage, LocalDate recordedDate, SourceType sourceType, UUID sourceEventId) {
}
