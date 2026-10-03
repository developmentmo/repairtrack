package com.repairtrack.vehicle.api;

import java.time.LocalDate;
import java.util.List;

import com.repairtrack.vehicle.infrastructure.registry.RegistryVehicle;

/** Public registry data; a suggestion for the client's form, never proof. */
record RegistryVehicleResponse(
        String licensePlate,
        String make,
        String model,
        String vehicleType,
        LocalDate firstRegistrationDate,
        LocalDate apkExpiryDate,
        String primaryColor,
        List<String> fuelTypes,
        String source
) {

    static RegistryVehicleResponse from(RegistryVehicle vehicle) {
        return new RegistryVehicleResponse(vehicle.licensePlate(), vehicle.make(), vehicle.model(),
                vehicle.vehicleType(), vehicle.firstRegistrationDate(), vehicle.apkExpiryDate(),
                vehicle.primaryColor(), vehicle.fuelTypes(), "RDW");
    }
}
