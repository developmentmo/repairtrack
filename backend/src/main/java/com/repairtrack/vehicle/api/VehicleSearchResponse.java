package com.repairtrack.vehicle.api;

import java.util.UUID;

import com.repairtrack.vehicle.application.VehicleSearchResult;

public record VehicleSearchResponse(UUID id, String licensePlate, String make, String model, Integer modelYear) {

    static VehicleSearchResponse from(VehicleSearchResult result) {
        return new VehicleSearchResponse(result.id(), result.licensePlate(), result.make(), result.model(),
                result.modelYear());
    }
}
