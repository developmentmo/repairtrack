package com.repairtrack.garage.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Garage registration. No verification status field: a new garage is always PENDING,
 * whatever a client sends.
 */
public record RegisterGarageRequest(
        @NotBlank @Size(max = 150) String name,
        @NotBlank @Pattern(regexp = "[0-9]{8}", message = "must be an 8-digit KvK number") String kvkNumber,
        @NotBlank @Size(max = 200) String address,
        @NotBlank @Pattern(regexp = "[1-9][0-9]{3} ?[A-Za-z]{2}", message = "must be a Dutch postal code like 1234 AB")
        String postalCode,
        @NotBlank @Size(max = 100) String city,
        @Pattern(regexp = "\\+?[0-9 ()\\-]{6,20}", message = "must be a valid phone number") String phone,
        @Email @Size(max = 254) String email
) {
}
