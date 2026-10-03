package com.repairtrack.security.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.repairtrack.common.crypto.OpaqueTokens;
import com.repairtrack.security.UserAccountEvents;
import com.repairtrack.security.UserRegisteredEvent;
import com.repairtrack.security.domain.AccountToken;
import com.repairtrack.security.domain.AccountTokenPurpose;
import com.repairtrack.security.domain.PasswordPolicy;
import com.repairtrack.security.domain.User;
import com.repairtrack.security.infrastructure.AccountTokenRepository;
import com.repairtrack.security.infrastructure.RefreshTokenRepository;
import com.repairtrack.security.infrastructure.UserRepository;

/**
 * Email verification and password reset with single-use links sent by email.
 * <p>
 * Requests by email address ({@link #resendVerification}, {@link #requestPasswordReset}) always succeed silently,
 * so they never reveal whether an account exists. Every link is single-use, only its hash is stored, and a new
 * email invalidates the previous link of the same kind.
 */
@Service
public class AccountService {

    private final UserRepository users;
    private final AccountTokenRepository tokens;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public AccountService(UserRepository users, AccountTokenRepository tokens, RefreshTokenRepository refreshTokens,
                          PasswordEncoder passwordEncoder, ApplicationEventPublisher events, Clock clock) {
        this.users = users;
        this.tokens = tokens;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.events = events;
        this.clock = clock;
    }

    /** Every new account gets a verification email (in the registration's transaction; sent after commit). */
    @EventListener
    public void on(UserRegisteredEvent event) {
        users.findById(event.userId()).ifPresent(user -> sendVerification(user, event.occurredAt()));
    }

    @Transactional
    public void resendVerification(String email) {
        activeUser(email)
                .filter(user -> !user.isEmailVerified())
                .ifPresent(user -> sendVerification(user, Instant.now(clock)));
    }

    @Transactional
    public void verifyEmail(String rawToken) {
        Instant now = Instant.now(clock);
        AccountToken token = usableToken(rawToken, AccountTokenPurpose.EMAIL_VERIFICATION, now);
        User user = users.findById(token.getUserId()).orElseThrow(InvalidAccountTokenException::new);
        token.use(now);
        if (!user.isEmailVerified()) {
            user.verifyEmail(now);
            events.publishEvent(new UserAccountEvents.EmailVerified(user.getId(), now));
        }
    }

    @Transactional
    public void requestPasswordReset(String email) {
        activeUser(email).ifPresent(user -> {
            Instant now = Instant.now(clock);
            String raw = issue(user, AccountTokenPurpose.PASSWORD_RESET, now);
            events.publishEvent(new AccountMails.PasswordResetRequested(user.getEmail(), user.getFirstName(), raw));
        });
    }

    /**
     * Sets a new password and logs the user out everywhere. Also confirms the email address: whoever received the
     * link controls the mailbox.
     */
    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        PasswordPolicy.validate(newPassword);
        Instant now = Instant.now(clock);
        AccountToken token = usableToken(rawToken, AccountTokenPurpose.PASSWORD_RESET, now);
        User user = users.findById(token.getUserId())
                .filter(User::isActive)
                .orElseThrow(InvalidAccountTokenException::new);
        token.use(now);
        user.changePasswordHash(passwordEncoder.encode(newPassword), now);
        user.verifyEmail(now);
        refreshTokens.revokeAllOfUser(user.getId(), now);
        events.publishEvent(new UserAccountEvents.PasswordReset(user.getId(), now));
    }

    private void sendVerification(User user, Instant now) {
        String raw = issue(user, AccountTokenPurpose.EMAIL_VERIFICATION, now);
        events.publishEvent(new AccountMails.VerificationRequested(user.getEmail(), user.getFirstName(), raw));
    }

    private String issue(User user, AccountTokenPurpose purpose, Instant now) {
        tokens.invalidateOpen(user.getId(), purpose, now);
        String raw = OpaqueTokens.generate();
        tokens.save(AccountToken.issue(user.getId(), purpose, OpaqueTokens.sha256Hex(raw), now));
        return raw;
    }

    private AccountToken usableToken(String rawToken, AccountTokenPurpose purpose, Instant now) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidAccountTokenException();
        }
        return tokens.findByTokenHash(OpaqueTokens.sha256Hex(rawToken.trim()))
                .filter(token -> token.isUsableFor(purpose, now))
                .orElseThrow(InvalidAccountTokenException::new);
    }

    private Optional<User> activeUser(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        return users.findByEmail(User.normalizeEmail(email)).filter(User::isActive);
    }
}
