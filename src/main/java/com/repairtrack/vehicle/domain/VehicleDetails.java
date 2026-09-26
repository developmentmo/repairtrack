package com.repairtrack.vehicle.domain;

import java.time.LocalDate;

/** The mutable attributes of a vehicle (everything except its VIN identity). */
public record VehicleDetails(
        String licensePlate,
        String make,
        String model,
        Integer modelYear,
        LocalDate firstRegistrationDate
) {
}
