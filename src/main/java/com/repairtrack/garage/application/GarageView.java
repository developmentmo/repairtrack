package com.repairtrack.garage.application;

import java.time.Instant;
import java.util.UUID;

import com.repairtrack.garage.GarageVerificationStatus;
import com.repairtrack.garage.domain.Garage;

public record GarageView(
        UUID id,
        String name,
        String kvkNumber,
        String address,
        String postalCode,
        String city,
        String phone,
        String email,
        GarageVerificationStatus verificationStatus,
        Instant verificationChangedAt,
        Instant createdAt
) {

    static GarageView of(Garage garage) {
        return new GarageView(garage.getId(), garage.getName(), garage.getKvkNumber(), garage.getAddress(),
                garage.getPostalCode(), garage.getCity(), garage.getPhone(), garage.getEmail(),
                garage.getVerificationStatus(), garage.getVerificationChangedAt(), garage.getCreatedAt());
    }
}
