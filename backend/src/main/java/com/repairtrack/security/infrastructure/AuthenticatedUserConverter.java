package com.repairtrack.security.infrastructure;

import java.util.List;
import java.util.UUID;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

import com.repairtrack.security.AuthenticatedUser;
import com.repairtrack.security.application.AccessTokenService;
import com.repairtrack.security.application.LoginSessionService;
import com.repairtrack.security.domain.User;

/**
 * Turns a validated JWT into an {@link AuthenticatedUserToken}.
 * <p>
 * Loads the user on every request (one primary-key lookup). Consequences:
 * blocking a user takes effect immediately, and roles/authorities come from the database,
 * never from token claims.
 * <p>
 * Also checks and extends the login session of the token: after logout or the idle timeout the access token stops
 * working at once, not when it expires. Tokens issued before sessions existed carry no session claim and are accepted
 * until they expire.
 * <p>
 * Not a Spring bean on purpose: Boot would register every {@code Converter} bean with MVC's
 * conversion service. It is created by {@link SecurityConfiguration}.
 */
class AuthenticatedUserConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserRepository users;
    private final LoginSessionService loginSessions;

    AuthenticatedUserConverter(UserRepository users, LoginSessionService loginSessions) {
        this.users = users;
        this.loginSessions = loginSessions;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        UUID userId = parseSubject(jwt);
        User user = users.findById(userId)
                .orElseThrow(() -> new InvalidBearerTokenException("Unknown token subject"));
        if (!user.isActive()) {
            throw new DisabledException("User account is not active");
        }
        UUID sessionId = parseSessionId(jwt);
        if (sessionId != null && !loginSessions.recordActivity(sessionId, userId)) {
            throw new InvalidBearerTokenException("Session is over");
        }
        List<GrantedAuthority> authorities = user.getRoles().stream()
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role.name()))
                .toList();
        AuthenticatedUser principal = new AuthenticatedUser(user.getId(), user.getEmail(), user.getRoles());
        return new AuthenticatedUserToken(principal, jwt, authorities);
    }

    private static UUID parseSessionId(Jwt jwt) {
        String claim = jwt.getClaimAsString(AccessTokenService.SESSION_ID_CLAIM);
        if (claim == null) {
            return null;
        }
        try {
            return UUID.fromString(claim);
        } catch (IllegalArgumentException ex) {
            throw new InvalidBearerTokenException("Invalid session claim");
        }
    }

    private static UUID parseSubject(Jwt jwt) {
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new InvalidBearerTokenException("Invalid token subject");
        }
    }
}
