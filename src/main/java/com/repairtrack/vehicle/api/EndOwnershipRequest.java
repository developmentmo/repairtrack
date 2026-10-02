package com.repairtrack.vehicle.api;

import java.time.LocalDate;

/** {@code endDate} defaults to today. */
public record EndOwnershipRequest(LocalDate endDate) {
}
