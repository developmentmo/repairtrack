package com.repairtrack.security;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * The authenticated caller, as seen by every other module.
 * <p>
 * Built per request from the database (not from token claims), so status and role changes take
 * effect immediately. Controllers obtain it with {@code @AuthenticationPrincipal} and pass it
 * explicitly to application services, which perform their own authorization checks.
 */
public record AuthenticatedUser(UUID id, String email, Set<Role> roles) {

    public AuthenticatedUser {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(email, "email");
        roles = Set.copyOf(roles);
    }

    public boolean hasRole(Role role) {
        return roles.contains(role);
    }
}
