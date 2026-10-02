package com.repairtrack.vehicle.api;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Registers a vehicle. Without {@code garageId} the caller becomes the owner (from {@code ownedSince},
 * default today). With {@code garageId} a garage member registers a customer's vehicle; it gets no owner.
 * <p>
 * Dates are checked against "today" in the business time zone by the domain, not with
 * {@code @PastOrPresent} (which would use the server's zone).
 */
public record RegisterVehicleRequest(
        @NotBlank @Size(max = 20) String vin,
        @Size(max = 15) String licensePlate,
        @NotBlank @Size(max = 100) String make,
        @NotBlank @Size(max = 100) String model,
        @Min(1886) @Max(2100) Integer modelYear,
        LocalDate firstRegistrationDate,
        UUID garageId,
        LocalDate ownedSince
) {
}
