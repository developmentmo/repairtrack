package com.repairtrack.vehicle.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.repairtrack.vehicle.application.VehicleView;
import com.repairtrack.vehicle.domain.VehicleStatus;

/** {@code vin} is null for callers who may not see it. Never contains owner information. */
public record VehicleResponse(
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

    static VehicleResponse from(VehicleView view) {
        return new VehicleResponse(view.id(), view.vin(), view.licensePlate(), view.make(), view.model(),
                view.modelYear(), view.firstRegistrationDate(), view.status(), view.ownedByMe(), view.canEdit(),
                view.createdAt(), view.updatedAt());
    }
}
