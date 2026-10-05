package com.repairtrack.security.application;

import java.time.Clock;
import java.time.Instant;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.repairtrack.common.crypto.OpaqueTokens;
import com.repairtrack.security.AuthenticatedUser;
import com.repairtrack.security.UserAccountEvents;
import com.repairtrack.security.domain.AccountTokenPurpose;
import com.repairtrack.security.domain.User;
import com.repairtrack.security.infrastructure.AccountTokenRepository;
import com.repairtrack.security.infrastructure.RefreshTokenRepository;
import com.repairtrack.security.infrastructure.UserRepository;

/**
 * A user deletes their own account (required by the App Store and Google Play).
 * <p>
 * Nothing is physically deleted: the vehicle history belongs to the vehicle. What happens, in one transaction:
 * the password is checked again; other modules end what the user still holds ({@link UserAccountEvents.AccountDeleted}:
 * vehicle ownerships end today, garage memberships end; a sole garage admin with colleagues is refused); e-mail
 * and name are replaced by placeholders, so nothing identifies the person any more; every session and e-mail link
 * stops working. The e-mail address can be used for a new account afterwards.
 */
@Service
public class AccountDeletionService {

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final AccountTokenRepository accountTokens;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public AccountDeletionService(UserRepository users, RefreshTokenRepository refreshTokens,
                                  AccountTokenRepository accountTokens, PasswordEncoder passwordEncoder,
                                  ApplicationEventPublisher events, Clock clock) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.accountTokens = accountTokens;
        this.passwordEncoder = passwordEncoder;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public void deleteOwnAccount(AuthenticatedUser actor, String password) {
        User user = users.findById(actor.id()).filter(User::isActive).orElseThrow(UserNotFoundException::new);
        if (password == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new PasswordIncorrectException();
        }
        Instant now = Instant.now(clock);
        // Other modules first: they may refuse (e.g. the last admin of a garage with other members).
        events.publishEvent(new UserAccountEvents.AccountDeleted(user.getId(), now));
        user.delete(passwordEncoder.encode(OpaqueTokens.generate()), now);
        users.save(user);
        refreshTokens.revokeAllOfUser(user.getId(), now); // also flushes the user change
        for (AccountTokenPurpose purpose : AccountTokenPurpose.values()) {
            accountTokens.invalidateOpen(user.getId(), purpose, now);
        }
    }
}
