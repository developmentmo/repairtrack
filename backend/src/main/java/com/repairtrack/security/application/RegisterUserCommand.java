package com.repairtrack.security.application;

/** Input for self-registration. Intentionally has no role or status: those are decided server-side. */
public record RegisterUserCommand(String email, String password, String firstName, String lastName) {

    @Override
    public String toString() {
        return "RegisterUserCommand[email=" + email + "]"; // never print the password
    }
}
