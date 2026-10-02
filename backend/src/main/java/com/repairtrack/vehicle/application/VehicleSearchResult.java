package com.repairtrack.vehicle.application;

import java.util.UUID;

/** Search hit. Never contains the VIN (see VehiclePermissions) or anything about owners. */
public record VehicleSearchResult(UUID id, String licensePlate, String make, String model, Integer modelYear) {
}
