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
import com.repairtrack.security.domain.User;

/**
 * Turns a validated JWT into an {@link AuthenticatedUserToken}.
 * <p>
 * Loads the user on every request (one primary-key lookup). Consequences:
 * blocking a user takes effect immediately, and roles/authorities come from the database,
 * never from token claims.
 * <p>
 * Not a Spring bean on purpose: Boot would register every {@code Converter} bean with MVC's
 * conversion service. It is created by {@link SecurityConfiguration}.
 */
class AuthenticatedUserConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserRepository users;

    AuthenticatedUserConverter(UserRepository users) {
        this.users = users;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        UUID userId = parseSubject(jwt);
        User user = users.findById(userId)
                .orElseThrow(() -> new InvalidBearerTokenException("Unknown token subject"));
        if (!user.isActive()) {
            throw new DisabledException("User account is not active");
        }
        List<GrantedAuthority> authorities = user.getRoles().stream()
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role.name()))
                .toList();
        AuthenticatedUser principal = new AuthenticatedUser(user.getId(), user.getEmail(), user.getRoles());
        return new AuthenticatedUserToken(principal, jwt, authorities);
    }

    private static UUID parseSubject(Jwt jwt) {
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new InvalidBearerTokenException("Invalid token subject");
        }
    }
}
