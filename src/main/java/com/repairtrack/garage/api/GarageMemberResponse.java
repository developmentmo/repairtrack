package com.repairtrack.garage.api;

import java.time.Instant;
import java.util.UUID;

import com.repairtrack.garage.GarageRole;
import com.repairtrack.garage.application.GarageMemberView;

public record GarageMemberResponse(
        UUID userId,
        String email,
        String firstName,
        String lastName,
        GarageRole role,
        Instant memberSince
) {

    static GarageMemberResponse from(GarageMemberView view) {
        return new GarageMemberResponse(view.userId(), view.email(), view.firstName(), view.lastName(),
                view.role(), view.memberSince());
    }
}
