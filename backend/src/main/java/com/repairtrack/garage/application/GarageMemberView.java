package com.repairtrack.garage.application;

import java.time.Instant;
import java.util.UUID;

import com.repairtrack.garage.GarageRole;

/** A member of a garage, as seen by other members of that garage. */
public record GarageMemberView(
        UUID userId,
        String email,
        String firstName,
        String lastName,
        GarageRole role,
        Instant memberSince
) {
}
