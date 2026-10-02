package com.repairtrack.vehicle.api;

import java.time.LocalDate;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Full replacement of the editable attributes. There is no VIN field: the VIN is the vehicle's
 * identity and cannot be edited.
 */
public record UpdateVehicleRequest(
        @Size(max = 15) String licensePlate,
        @NotBlank @Size(max = 100) String make,
        @NotBlank @Size(max = 100) String model,
        @Min(1886) @Max(2100) Integer modelYear,
        LocalDate firstRegistrationDate
) {
}
