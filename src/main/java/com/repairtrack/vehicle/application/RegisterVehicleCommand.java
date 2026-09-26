package com.repairtrack.vehicle.application;

import java.time.LocalDate;
import java.util.UUID;

import com.repairtrack.vehicle.domain.VehicleDetails;

/**
 * Register a vehicle either as its owner ({@code garageId == null}; the caller becomes owner from
 * {@code ownedSince}, default today) or on behalf of a garage ({@code garageId} set; no owner).
 */
public record RegisterVehicleCommand(String vin, VehicleDetails details, UUID garageId, LocalDate ownedSince) {
}
