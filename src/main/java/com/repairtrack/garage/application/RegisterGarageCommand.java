package com.repairtrack.garage.application;

/** Input for registering a garage. Has no verification status: every new garage starts PENDING. */
public record RegisterGarageCommand(
        String name,
        String kvkNumber,
        String address,
        String postalCode,
        String city,
        String phone,
        String email
) {
}
