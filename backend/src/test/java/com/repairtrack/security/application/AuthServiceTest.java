package com.repairtrack.security.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.repairtrack.common.crypto.OpaqueTokens;
import com.repairtrack.common.ratelimit.FixedWindowRateLimiter;
import com.repairtrack.security.Role;
import com.repairtrack.security.UserRegisteredEvent;
import com.repairtrack.security.domain.InvalidPasswordException;
import com.repairtrack.security.domain.RefreshToken;
import com.repairtrack.security.domain.User;
import com.repairtrack.security.infrastructure.RefreshTokenRepository;
import com.repairtrack.security.infrastructure.SecurityProperties;
import com.repairtrack.security.infrastructure.UserRepository;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-25T10:00:00Z");
    private static final String PASSWORD = "correct horse battery staple";
    private static final Duration REFRESH_TTL = Duration.ofDays(30);
    private static final int MAX_FAILED_LOGINS = 3;

    // A real encoder: hashing behaviour is part of what we verify, and BCrypt is fast enough.
    private static final PasswordEncoder PASSWORD_ENCODER = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    @Mock
    private UserRepository users;
    @Mock
    private RefreshTokenRepository refreshTokens;
    @Mock
    private AccessTokenService accessTokenService;
    @Mock
    private ApplicationEventPublisher events;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        var properties = new SecurityProperties(
                new SecurityProperties.Jwt("0123456789abcdef0123456789abcdef", "repairtrack", Duration.ofMinutes(15)),
                REFRESH_TTL);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        var loginThrottle = new LoginThrottle(new FixedWindowRateLimiter(MAX_FAILED_LOGINS, Duration.ofMinutes(15), clock));
        authService = new AuthService(users, refreshTokens, PASSWORD_ENCODER, accessTokenService,
                properties, events, clock, loginThrottle);
    }

    // ---------- register ----------

    @Test
    void registerCreatesOwnerWithHashedPasswordAndPublishesEvent() {
        when(users.existsByEmail("new@example.com")).thenReturn(false);
        when(users.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserProfile profile = authService.register(
                new RegisterUserCommand(" New@Example.com ", PASSWORD, "Nina", "Nieuw"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).saveAndFlush(saved.capture());
        User user = saved.getValue();
        assertThat(user.getEmail()).isEqualTo("new@example.com");
        assertThat(user.getRoles()).containsExactly(Role.OWNER);
        assertThat(user.getPasswordHash()).isNotEqualTo(PASSWORD);
        assertThat(PASSWORD_ENCODER.matches(PASSWORD, user.getPasswordHash())).isTrue();
        assertThat(profile.roles()).containsExactly(Role.OWNER);
        verify(events).publishEvent(new UserRegisteredEvent(user.getId(), NOW));
    }

    @Test
    void registerRejectsExistingEmail() {
        when(users.existsByEmail("taken@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(
                new RegisterUserCommand("Taken@example.com", PASSWORD, "A", "B")))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void registerRejectsWeakPasswordBeforeTouchingTheDatabase() {
        assertThatThrownBy(() -> authService.register(new RegisterUserCommand("a@example.com", "short", "A", "B")))
                .isInstanceOf(InvalidPasswordException.class);
        verify(users, never()).existsByEmail(anyString());
    }

    // ---------- login ----------

    @Test
    void loginWithUnknownEmailFailsWithGenericError() {
        when(users.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login("nobody@example.com", PASSWORD))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void loginWithWrongPasswordFailsWithGenericError() {
        User user = existingUser();
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(user.getEmail(), "not the right password"))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(refreshTokens, never()).save(any());
    }

    @Test
    void loginOfBlockedUserIsRefused() {
        User user = existingUser();
        user.block(NOW);
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(user.getEmail(), PASSWORD))
                .isInstanceOf(AccountBlockedException.class);
        verify(refreshTokens, never()).save(any());
    }

    @Test
    void successfulLoginStoresOnlyTheHashOfTheRefreshToken() {
        User user = existingUser();
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(accessTokenService.issue(user.getId()))
                .thenReturn(new AccessTokenService.IssuedAccessToken("jwt", NOW.plusSeconds(900)));

        AuthTokens tokens = authService.login(user.getEmail(), PASSWORD);

        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokens).save(saved.capture());
        assertThat(tokens.accessToken()).isEqualTo("jwt");
        assertThat(tokens.refreshTokenExpiresAt()).isEqualTo(NOW.plus(REFRESH_TTL));
        assertThat(saved.getValue().getUserId()).isEqualTo(user.getId());
        verify(refreshTokens, never()).findByTokenHash(tokens.refreshToken()); // raw token never used as key
    }

    @Test
    void unverifiedEmailCannotLogInButOnlyAfterTheRightPassword() {
        User user = User.register("new@example.com", PASSWORD_ENCODER.encode(PASSWORD), "Nina", "New", NOW);
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(user.getEmail(), "wrong password"))
                .isInstanceOf(InvalidCredentialsException.class);
        assertThatThrownBy(() -> authService.login(user.getEmail(), PASSWORD))
                .isInstanceOf(EmailNotVerifiedException.class);
        verify(refreshTokens, never()).save(any());
    }

    @Test
    void tooManyFailedLoginsBlockTheAccountTemporarilyEvenWithTheRightPassword() {
        User user = existingUser();
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        for (int i = 0; i < MAX_FAILED_LOGINS; i++) {
            assertThatThrownBy(() -> authService.login(user.getEmail(), "wrong password"))
                    .isInstanceOf(InvalidCredentialsException.class);
        }

        assertThatThrownBy(() -> authService.login("  Owner@Example.com ", PASSWORD))
                .isInstanceOf(TooManyLoginAttemptsException.class)
                .satisfies(e -> assertThat(((TooManyLoginAttemptsException) e).retryAfter()).isPositive());
        verify(refreshTokens, never()).save(any());
    }

    @Test
    void unknownEmailsAreThrottledTheSameWay() {
        when(users.findByEmail("nobody@example.com")).thenReturn(Optional.empty());
        for (int i = 0; i < MAX_FAILED_LOGINS; i++) {
            assertThatThrownBy(() -> authService.login("nobody@example.com", PASSWORD))
                    .isInstanceOf(InvalidCredentialsException.class);
        }

        assertThatThrownBy(() -> authService.login("nobody@example.com", PASSWORD))
                .isInstanceOf(TooManyLoginAttemptsException.class);
    }

    @Test
    void successfulLoginResetsTheFailureCount() {
        User user = existingUser();
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(accessTokenService.issue(user.getId()))
                .thenReturn(new AccessTokenService.IssuedAccessToken("jwt", NOW.plusSeconds(900)));
        for (int i = 0; i < MAX_FAILED_LOGINS - 1; i++) {
            assertThatThrownBy(() -> authService.login(user.getEmail(), "wrong password"))
                    .isInstanceOf(InvalidCredentialsException.class);
        }
        authService.login(user.getEmail(), PASSWORD);

        for (int i = 0; i < MAX_FAILED_LOGINS - 1; i++) {
            assertThatThrownBy(() -> authService.login(user.getEmail(), "wrong password"))
                    .isInstanceOf(InvalidCredentialsException.class);
        }
    }

    // ---------- refresh ----------

    @Test
    void refreshRotatesTokenWithinSameFamily() {
        User user = existingUser();
        UUID family = UUID.randomUUID();
        String raw = OpaqueTokens.generate();
        RefreshToken current = RefreshToken.issue(user.getId(), family, OpaqueTokens.sha256Hex(raw),
                NOW.minusSeconds(60), REFRESH_TTL);
        when(refreshTokens.findByTokenHash(OpaqueTokens.sha256Hex(raw))).thenReturn(Optional.of(current));
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        when(accessTokenService.issue(user.getId()))
                .thenReturn(new AccessTokenService.IssuedAccessToken("jwt2", NOW.plusSeconds(900)));

        AuthTokens tokens = authService.refresh(raw);

        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokens).save(saved.capture());
        RefreshToken successor = saved.getValue();
        assertThat(successor.getFamilyId()).isEqualTo(family);
        assertThat(current.isRevoked()).isTrue();
        assertThat(current.getReplacedById()).isEqualTo(successor.getId());
        assertThat(tokens.refreshToken()).isNotEqualTo(raw);
    }

    @Test
    void reusedRefreshTokenRevokesWholeFamily() {
        UUID family = UUID.randomUUID();
        String raw = OpaqueTokens.generate();
        RefreshToken revoked = RefreshToken.issue(UUID.randomUUID(), family, OpaqueTokens.sha256Hex(raw),
                NOW.minusSeconds(60), REFRESH_TTL);
        revoked.revoke(NOW.minusSeconds(30));
        when(refreshTokens.findByTokenHash(OpaqueTokens.sha256Hex(raw))).thenReturn(Optional.of(revoked));

        assertThatThrownBy(() -> authService.refresh(raw)).isInstanceOf(InvalidRefreshTokenException.class);
        verify(refreshTokens).revokeFamily(family, NOW);
        verify(refreshTokens, never()).save(any());
    }

    @Test
    void expiredRefreshTokenIsRejected() {
        String raw = OpaqueTokens.generate();
        RefreshToken expired = RefreshToken.issue(UUID.randomUUID(), UUID.randomUUID(), OpaqueTokens.sha256Hex(raw),
                NOW.minus(REFRESH_TTL).minusSeconds(1), REFRESH_TTL);
        when(refreshTokens.findByTokenHash(OpaqueTokens.sha256Hex(raw))).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> authService.refresh(raw)).isInstanceOf(InvalidRefreshTokenException.class);
        verify(refreshTokens, never()).save(any());
    }

    @Test
    void unknownRefreshTokenIsRejected() {
        when(refreshTokens.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh("unknown")).isInstanceOf(InvalidRefreshTokenException.class);
    }

    private static User existingUser() {
        User user = User.register("owner@example.com", PASSWORD_ENCODER.encode(PASSWORD), "Olga", "Owner",
                NOW.minus(Duration.ofDays(10)));
        user.verifyEmail(NOW.minus(Duration.ofDays(10)));
        return user;
    }
}
