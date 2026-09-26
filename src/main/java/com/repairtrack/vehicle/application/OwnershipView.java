package com.repairtrack.vehicle.application;

import java.time.LocalDate;
import java.util.UUID;

import com.repairtrack.vehicle.domain.OwnershipStatus;
import com.repairtrack.vehicle.domain.VehicleOwnership;

public record OwnershipView(UUID vehicleId, LocalDate startDate, LocalDate endDate, OwnershipStatus status) {

    static OwnershipView of(VehicleOwnership ownership) {
        return new OwnershipView(ownership.getVehicleId(), ownership.getStartDate(), ownership.getEndDate(),
                ownership.getStatus());
    }
}
