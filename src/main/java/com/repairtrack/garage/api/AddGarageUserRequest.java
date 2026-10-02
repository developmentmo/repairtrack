package com.repairtrack.garage.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.repairtrack.garage.GarageRole;

/** Sent by a garage admin. The user must already have a RepairTrack account. */
public record AddGarageUserRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotNull GarageRole role
) {
}
