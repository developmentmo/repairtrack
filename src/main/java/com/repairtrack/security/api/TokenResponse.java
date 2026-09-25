package com.repairtrack.security.api;

import java.time.Instant;

import com.repairtrack.security.application.AuthTokens;

public record TokenResponse(
        String tokenType,
        String accessToken,
        Instant accessTokenExpiresAt,
        String refreshToken,
        Instant refreshTokenExpiresAt
) {

    static TokenResponse from(AuthTokens tokens) {
        return new TokenResponse("Bearer", tokens.accessToken(), tokens.accessTokenExpiresAt(),
                tokens.refreshToken(), tokens.refreshTokenExpiresAt());
    }

    @Override
    public String toString() {
        return "TokenResponse[accessTokenExpiresAt=" + accessTokenExpiresAt + "]";
    }
}
