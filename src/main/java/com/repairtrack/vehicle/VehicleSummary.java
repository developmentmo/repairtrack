package com.repairtrack.vehicle;

import java.util.UUID;

/** Non-sensitive vehicle identification for other modules' responses. Never contains the VIN or owner. */
public record VehicleSummary(UUID id, String licensePlate, String make, String model, Integer modelYear) {
}
