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

import com.repairtrack.common.crypto.OpaqueTokens;
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
    private final LoginThrottle loginThrottle;
    private final LoginSessionService loginSessions;

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
                       Clock clock,
                       LoginThrottle loginThrottle,
                       LoginSessionService loginSessions) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.accessTokenService = accessTokenService;
        this.properties = properties;
        this.events = events;
        this.clock = clock;
        this.loginThrottle = loginThrottle;
        this.loginSessions = loginSessions;
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
        String normalizedEmail = User.normalizeEmail(email);
        loginThrottle.checkNotBlocked(normalizedEmail);
        Optional<User> candidate = users.findByEmail(normalizedEmail);
        if (candidate.isEmpty()) {
            passwordEncoder.matches(password, dummyPasswordHash);
            loginThrottle.recordFailure(normalizedEmail);
            throw new InvalidCredentialsException();
        }
        User user = candidate.get();
        if (!passwordEncoder.matches(password, user.getPasswordHash())
                || user.getStatus() == UserStatus.DELETED) {
            loginThrottle.recordFailure(normalizedEmail);
            throw new InvalidCredentialsException();
        }
        if (user.getStatus() == UserStatus.BLOCKED) {
            throw new AccountBlockedException();
        }
        loginThrottle.reset(normalizedEmail);
        // Only after a correct password, so the answer does not reveal unverified accounts to others.
        if (!user.isEmailVerified()) {
            throw new EmailNotVerifiedException();
        }
        Instant now = Instant.now(clock);
        return issueTokens(user.getId(), loginSessions.start(user.getId(), now).getId(), now);
    }

    /**
     * Rotates a refresh token. {@code noRollbackFor}: when reuse is detected, the family
     * revocation must be committed even though the request fails.
     */
    @Transactional(noRollbackFor = {InvalidRefreshTokenException.class, AccountBlockedException.class,
            SessionExpiredException.class})
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
        if (!loginSessions.recordActivity(current.getFamilyId(), user.getId())) {
            // Idle for too long (or logged out elsewhere): this login is over for good.
            refreshTokens.revokeFamily(current.getFamilyId(), now);
            loginSessions.end(current.getFamilyId(), now);
            throw new SessionExpiredException();
        }

        String rawSuccessor = OpaqueTokens.generate();
        RefreshToken successor = RefreshToken.issue(user.getId(), current.getFamilyId(),
                OpaqueTokens.sha256Hex(rawSuccessor), now, properties.refreshTokenTtl());
        refreshTokens.save(successor);
        current.rotateTo(successor, now);

        AccessTokenService.IssuedAccessToken accessToken = accessTokenService.issue(user.getId(), current.getFamilyId());
        return new AuthTokens(accessToken.value(), accessToken.expiresAt(), rawSuccessor, successor.getExpiresAt());
    }

    /** Ends the login session the token belongs to. Idempotent; unknown tokens are ignored. */
    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokens.findByTokenHash(OpaqueTokens.sha256Hex(rawRefreshToken))
                .ifPresent(token -> {
                    Instant now = Instant.now(clock);
                    refreshTokens.revokeFamily(token.getFamilyId(), now);
                    loginSessions.end(token.getFamilyId(), now);
                });
    }

    /** {@code familyId} is the id of the login session. */
    private AuthTokens issueTokens(UUID userId, UUID familyId, Instant now) {
        String rawRefreshToken = OpaqueTokens.generate();
        RefreshToken refreshToken = RefreshToken.issue(userId, familyId,
                OpaqueTokens.sha256Hex(rawRefreshToken), now, properties.refreshTokenTtl());
        refreshTokens.save(refreshToken);
        AccessTokenService.IssuedAccessToken accessToken = accessTokenService.issue(userId, familyId);
        return new AuthTokens(accessToken.value(), accessToken.expiresAt(), rawRefreshToken, refreshToken.getExpiresAt());
    }
}
