package com.repairtrack.vehicle.api;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code vin} is the proof of access to the vehicle's documents; {@code ownedSince} defaults to today. */
public record ClaimVehicleRequest(@NotBlank @Size(max = 20) String vin, LocalDate ownedSince) {
}
