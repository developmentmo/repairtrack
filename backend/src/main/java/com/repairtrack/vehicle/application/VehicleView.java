package com.repairtrack.vehicle.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.repairtrack.vehicle.domain.VehicleStatus;

/**
 * A vehicle as seen by a specific caller. {@code vin} is null when the caller may not see it.
 * {@code ownedByMe} / {@code canEdit} let clients show the right actions without re-deriving rules.
 */
public record VehicleView(
        UUID id,
        String vin,
        String licensePlate,
        String make,
        String model,
        Integer modelYear,
        LocalDate firstRegistrationDate,
        VehicleStatus status,
        boolean ownedByMe,
        boolean canEdit,
        Instant createdAt,
        Instant updatedAt
) {
}
