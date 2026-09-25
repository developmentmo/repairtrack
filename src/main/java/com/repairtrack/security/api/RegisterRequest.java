package com.repairtrack.security.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.repairtrack.security.domain.PasswordPolicy;

/**
 * Self-registration. There is intentionally no role, status or garage field: any such
 * property sent by a client is ignored and can never influence the created account.
 */
public record RegisterRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = PasswordPolicy.MIN_LENGTH, max = PasswordPolicy.MAX_BYTES) String password,
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName
) {

    @Override
    public String toString() {
        return "RegisterRequest[email=" + email + "]";
    }
}
