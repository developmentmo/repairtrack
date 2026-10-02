package com.repairtrack.vehicle;

import java.time.LocalDate;

/**
 * What a shared vehicle report may show about the vehicle itself. No VIN (it is the claim proof),
 * no IDs, nothing about owners except how many ownership periods have been registered.
 */
public record VehiclePublicProfile(
        String make,
        String model,
        Integer modelYear,
        LocalDate firstRegistrationDate,
        String licensePlate,
        long registeredOwnerCount
) {
}
