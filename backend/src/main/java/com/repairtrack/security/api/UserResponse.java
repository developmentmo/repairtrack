package com.repairtrack.security.api;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import com.repairtrack.security.Role;
import com.repairtrack.security.application.UserProfile;
import com.repairtrack.security.domain.UserStatus;

public record UserResponse(
        UUID id,
        String email,
        String firstName,
        String lastName,
        UserStatus status,
        Set<Role> roles,
        boolean emailVerified,
        Instant createdAt
) {

    static UserResponse from(UserProfile profile) {
        return new UserResponse(profile.id(), profile.email(), profile.firstName(), profile.lastName(),
                profile.status(), profile.roles(), profile.emailVerified(), profile.createdAt());
    }
}
