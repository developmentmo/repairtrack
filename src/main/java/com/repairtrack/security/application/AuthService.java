package com.repairtrack.security.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.repairtrack.security.UserRegisteredEvent;
import com.repairtrack.security.domain.PasswordPolicy;
import com.repairtrack.security.domain.RefreshToken;
import com.repairtrack.security.domain.User;
import com.repairtrack.security.domain.UserStatus;
import com.repairtrack.security.infrastructure.RefreshTokenRepository;
import com.repairtrack.security.infrastructure.SecurityProperties;
import com.repairtrack.security.infrastructure.UserRepository;

/**
 * Registration, login, refresh-token rotation and logout.
 */
@Service
public class AuthService {

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenService accessTokenService;
    private final SecurityProperties properties;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    /**
     * Hash compared against when the email is unknown, so login takes about as long for unknown
     * emails as for wrong passwords (prevents account enumeration by timing).
     */
    private final String dummyPasswordHash;

    public AuthService(UserRepository users,
                       RefreshTokenRepository refreshTokens,
                       PasswordEncoder passwordEncoder,
                       AccessTokenService accessTokenService,
                       SecurityProperties properties,
                       ApplicationEventPublisher events,
                       Clock clock) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.accessTokenService = accessTokenService;
        this.properties = properties;
        this.events = events;
        this.clock = clock;
        this.dummyPasswordHash = passwordEncoder.encode(OpaqueTokens.generate());
    }

    @Transactional
    public UserProfile register(RegisterUserCommand command) {
        PasswordPolicy.validate(command.password());
        String email = User.normalizeEmail(command.email());
        if (users.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException();
        }
        Instant now = Instant.now(clock);
        User user = User.register(email, passwordEncoder.encode(command.password()),
                command.firstName(), command.lastName(), now);
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            // concurrent registration with the same email hit the unique constraint
            throw new EmailAlreadyRegisteredException();
        }
        events.publishEvent(new UserRegisteredEvent(user.getId(), now));
        return UserProfile.of(user);
    }

    @Transactional
    public AuthTokens login(String email, String password) {
        Optional<User> candidate = users.findByEmail(User.normalizeEmail(email));
        if (candidate.isEmpty()) {
            passwordEncoder.matches(password, dummyPasswordHash);
            throw new InvalidCredentialsException();
        }
        User user = candidate.get();
        if (!passwordEncoder.matches(password, user.getPasswordHash())
                || user.getStatus() == UserStatus.DELETED) {
            throw new InvalidCredentialsException();
        }
        if (user.getStatus() == UserStatus.BLOCKED) {
            throw new AccountBlockedException();
        }
        return issueTokens(user.getId(), UUID.randomUUID(), Instant.now(clock));
    }

    /**
     * Rotates a refresh token. {@code noRollbackFor}: when reuse is detected, the family
     * revocation must be committed even though the request fails.
     */
    @Transactional(noRollbackFor = {InvalidRefreshTokenException.class, AccountBlockedException.class})
    public AuthTokens refresh(String rawRefreshToken) {
        Instant now = Instant.now(clock);
        RefreshToken current = refreshTokens.findByTokenHash(OpaqueTokens.sha256Hex(rawRefreshToken))
                .orElseThrow(InvalidRefreshTokenException::new);

        if (current.isRevoked()) {
            // Replay of a rotated or logged-out token: assume theft, kill the whole login session.
            refreshTokens.revokeFamily(current.getFamilyId(), now);
            throw new InvalidRefreshTokenException();
        }
        if (current.isExpired(now)) {
            throw new InvalidRefreshTokenException();
        }

        User user = users.findById(current.getUserId()).orElseThrow(InvalidRefreshTokenException::new);
        if (!user.isActive()) {
            refreshTokens.revokeFamily(current.getFamilyId(), now);
            if (user.getStatus() == UserStatus.BLOCKED) {
                throw new AccountBlockedException();
            }
            throw new InvalidRefreshTokenException();
        }

        String rawSuccessor = OpaqueTokens.generate();
        RefreshToken successor = RefreshToken.issue(user.getId(), current.getFamilyId(),
                OpaqueTokens.sha256Hex(rawSuccessor), now, properties.refreshTokenTtl());
        refreshTokens.save(successor);
        current.rotateTo(successor, now);

        AccessTokenService.IssuedAccessToken accessToken = accessTokenService.issue(user.getId());
        return new AuthTokens(accessToken.value(), accessToken.expiresAt(), rawSuccessor, successor.getExpiresAt());
    }

    /** Ends the login session the token belongs to. Idempotent; unknown tokens are ignored. */
    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokens.findByTokenHash(OpaqueTokens.sha256Hex(rawRefreshToken))
                .ifPresent(token -> refreshTokens.revokeFamily(token.getFamilyId(), Instant.now(clock)));
    }

    private AuthTokens issueTokens(UUID userId, UUID familyId, Instant now) {
        String rawRefreshToken = OpaqueTokens.generate();
        RefreshToken refreshToken = RefreshToken.issue(userId, familyId,
                OpaqueTokens.sha256Hex(rawRefreshToken), now, properties.refreshTokenTtl());
        refreshTokens.save(refreshToken);
        AccessTokenService.IssuedAccessToken accessToken = accessTokenService.issue(userId);
        return new AuthTokens(accessToken.value(), accessToken.expiresAt(), rawRefreshToken, refreshToken.getExpiresAt());
    }
}
