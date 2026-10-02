package com.repairtrack.security;

import java.util.UUID;

/** Minimal, credential-free view of a user for other modules (e.g. listing garage members). */
public record UserSummary(UUID id, String email, String firstName, String lastName, boolean active) {
}
