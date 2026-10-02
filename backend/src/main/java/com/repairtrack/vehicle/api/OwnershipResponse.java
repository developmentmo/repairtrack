package com.repairtrack.vehicle.api;

import java.time.LocalDate;
import java.util.UUID;

import com.repairtrack.vehicle.application.OwnershipView;
import com.repairtrack.vehicle.domain.OwnershipStatus;

public record OwnershipResponse(UUID vehicleId, LocalDate startDate, LocalDate endDate, OwnershipStatus status) {

    static OwnershipResponse from(OwnershipView view) {
        return new OwnershipResponse(view.vehicleId(), view.startDate(), view.endDate(), view.status());
    }
}
