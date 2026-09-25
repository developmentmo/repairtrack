package com.repairtrack.security.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank @Size(max = 254) String email,
        // upper bound only to avoid hashing arbitrarily large input
        @NotBlank @Size(max = 256) String password
) {

    @Override
    public String toString() {
        return "LoginRequest[email=" + email + "]";
    }
}
