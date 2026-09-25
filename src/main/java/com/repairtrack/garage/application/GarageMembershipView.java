package com.repairtrack.garage.application;

import java.time.Instant;
import java.util.UUID;

import com.repairtrack.garage.GarageRole;
import com.repairtrack.garage.GarageVerificationStatus;

/** A garage the current user works at, with their role there. */
public record GarageMembershipView(
        UUID garageId,
        String garageName,
        String city,
        GarageVerificationStatus verificationStatus,
        GarageRole role,
        Instant memberSince
) {
}
