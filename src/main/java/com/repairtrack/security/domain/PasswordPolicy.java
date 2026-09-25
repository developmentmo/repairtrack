package com.repairtrack.security.domain;

import java.nio.charset.StandardCharsets;

/**
 * Password rules enforced by the backend, independent of client-side validation.
 * <p>
 * The 72-byte maximum is a hard BCrypt limit (bytes, not characters): longer input would be
 * truncated by the algorithm, so it is rejected instead.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 12;
    public static final int MAX_BYTES = 72;

    private PasswordPolicy() {
    }

    public static void validate(String rawPassword) {
        if (rawPassword == null || rawPassword.codePointCount(0, rawPassword.length()) < MIN_LENGTH) {
            throw new InvalidPasswordException("Password must be at least " + MIN_LENGTH + " characters long.");
        }
        if (rawPassword.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new InvalidPasswordException("Password must not exceed " + MAX_BYTES + " bytes.");
        }
        if (rawPassword.isBlank()) {
            throw new InvalidPasswordException("Password must not be blank.");
        }
    }
}
