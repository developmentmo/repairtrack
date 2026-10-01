package com.repairtrack.repair.domain;

import java.time.LocalDate;

import com.repairtrack.repair.RepairEventType;

/** Requested correction; {@code null} means "unchanged". */
public record RepairChanges(RepairEventType eventType, LocalDate eventDate, Integer mileage, String title,
                            String description) {
}
