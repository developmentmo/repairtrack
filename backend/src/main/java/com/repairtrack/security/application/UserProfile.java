package com.repairtrack.security.application;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import com.repairtrack.security.Role;
import com.repairtrack.security.domain.User;
import com.repairtrack.security.domain.UserStatus;

/** Read model of a user's own profile. Contains no credentials. */
public record UserProfile(
        UUID id,
        String email,
        String firstName,
        String lastName,
        UserStatus status,
        Set<Role> roles,
        boolean emailVerified,
        Instant createdAt
) {

    static UserProfile of(User user) {
        return new UserProfile(user.getId(), user.getEmail(), user.getFirstName(), user.getLastName(),
                user.getStatus(), user.getRoles(), user.isEmailVerified(), user.getCreatedAt());
    }
}
