package com.repairtrack.security.application;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import com.repairtrack.security.infrastructure.SecurityProperties;

/**
 * Issues short-lived access tokens.
 * <p>
 * Claims are minimal: subject (user ID), login session ID ({@value #SESSION_ID_CLAIM}), issuer, issued-at, expiry,
 * token ID. No email, name or
 * roles: tokens are readable by anyone holding them, and roles are loaded from the database per request.
 */
@Service
public class AccessTokenService {

    /** The {@link com.repairtrack.security.domain.LoginSession} the token belongs to. */
    public static final String SESSION_ID_CLAIM = "sid";

    private final JwtEncoder jwtEncoder;
    private final SecurityProperties properties;
    private final Clock clock;

    public AccessTokenService(JwtEncoder jwtEncoder, SecurityProperties properties, Clock clock) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
        this.clock = clock;
    }

    public IssuedAccessToken issue(UUID userId, UUID sessionId) {
        Instant now = Instant.now(clock);
        Instant expiresAt = now.plus(properties.jwt().accessTokenTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.jwt().issuer())
                .subject(userId.toString())
                .claim(SESSION_ID_CLAIM, sessionId.toString())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedAccessToken(token, expiresAt);
    }

    public record IssuedAccessToken(String value, Instant expiresAt) {

        @Override
        public String toString() {
            return "IssuedAccessToken[expiresAt=" + expiresAt + "]";
        }
    }
}
