package com.repairtrack.security.application;

import java.time.Instant;

public record AuthTokens(
        String accessToken,
        Instant accessTokenExpiresAt,
        String refreshToken,
        Instant refreshTokenExpiresAt
) {

    @Override
    public String toString() {
        return "AuthTokens[accessTokenExpiresAt=" + accessTokenExpiresAt
                + ", refreshTokenExpiresAt=" + refreshTokenExpiresAt + "]"; // never print tokens
    }
}
