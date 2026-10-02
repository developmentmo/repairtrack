package com.repairtrack.vehicle;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Events published by the vehicle module. Consumers: audit (Phase 5), notifications. IDs only. */
public final class VehicleEvents {

    private VehicleEvents() {
    }

    /** {@code garageId} is set when a garage registered the vehicle on behalf of a customer. */
    public record VehicleRegistered(UUID vehicleId, UUID registeredBy, UUID garageId, Instant occurredAt) {
    }

    public record VehicleDetailsChanged(UUID vehicleId, UUID changedBy, List<VehicleFieldChange> changes,
                                        Instant occurredAt) {
    }

    /** Ownership started, either by registering as owner or by claiming (audit action VEHICLE_CLAIMED). */
    public record VehicleOwnershipStarted(UUID vehicleId, UUID userId, LocalDate startDate, boolean claimed,
                                          Instant occurredAt) {
    }

    public record VehicleOwnershipEnded(UUID vehicleId, UUID userId, LocalDate endDate, Instant occurredAt) {
    }
}
