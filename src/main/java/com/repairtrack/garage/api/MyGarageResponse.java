package com.repairtrack.garage.api;

import java.time.Instant;
import java.util.UUID;

import com.repairtrack.garage.GarageRole;
import com.repairtrack.garage.GarageVerificationStatus;
import com.repairtrack.garage.application.GarageMembershipView;

public record MyGarageResponse(
        UUID garageId,
        String name,
        String city,
        GarageVerificationStatus verificationStatus,
        GarageRole role,
        Instant memberSince
) {

    static MyGarageResponse from(GarageMembershipView view) {
        return new MyGarageResponse(view.garageId(), view.garageName(), view.city(), view.verificationStatus(),
                view.role(), view.memberSince());
    }
}
