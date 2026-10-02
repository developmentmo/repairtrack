package com.repairtrack.garage.api;

import java.time.Instant;
import java.util.UUID;

import com.repairtrack.garage.GarageVerificationStatus;
import com.repairtrack.garage.application.GarageView;

public record GarageResponse(
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

    static GarageResponse from(GarageView view) {
        return new GarageResponse(view.id(), view.name(), view.kvkNumber(), view.address(), view.postalCode(),
                view.city(), view.phone(), view.email(), view.verificationStatus(), view.verificationChangedAt(),
                view.createdAt());
    }
}
