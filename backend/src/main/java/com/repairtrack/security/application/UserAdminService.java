package com.repairtrack.security.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.repairtrack.security.AuthenticatedUser;
import com.repairtrack.security.Role;
import com.repairtrack.security.UserAccountEvents;
import com.repairtrack.security.domain.User;
import com.repairtrack.security.domain.UserStatus;
import com.repairtrack.security.infrastructure.RefreshTokenRepository;
import com.repairtrack.security.infrastructure.UserRepository;

/**
 * User administration for SYSTEM_ADMIN: look up an account by exact email, block and unblock. Blocking takes
 * effect immediately (users are loaded per request) and also ends all login sessions.
 */
@Service
public class UserAdminService {

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public UserAdminService(UserRepository users, RefreshTokenRepository refreshTokens,
                            ApplicationEventPublisher events, Clock clock) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.events = events;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Optional<UserProfile> findByEmail(AuthenticatedUser actor, String email) {
        requireSystemAdmin(actor);
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        return users.findByEmail(User.normalizeEmail(email)).map(UserProfile::of);
    }

    @Transactional
    public UserProfile block(AuthenticatedUser actor, UUID userId) {
        requireSystemAdmin(actor);
        if (actor.id().equals(userId)) {
            throw new CannotBlockYourselfException();
        }
        User user = users.findById(userId).orElseThrow(UserNotFoundException::new);
        if (user.getStatus() != UserStatus.BLOCKED) {
            Instant now = Instant.now(clock);
            user.block(now);
            refreshTokens.revokeAllOfUser(userId, now);
            events.publishEvent(new UserAccountEvents.UserBlocked(userId, actor.id(), now));
        }
        return UserProfile.of(user);
    }

    @Transactional
    public UserProfile unblock(AuthenticatedUser actor, UUID userId) {
        requireSystemAdmin(actor);
        User user = users.findById(userId).orElseThrow(UserNotFoundException::new);
        if (user.getStatus() == UserStatus.BLOCKED) {
            Instant now = Instant.now(clock);
            user.unblock(now);
            events.publishEvent(new UserAccountEvents.UserUnblocked(userId, actor.id(), now));
        }
        return UserProfile.of(user);
    }

    private static void requireSystemAdmin(AuthenticatedUser actor) {
        if (!actor.hasRole(Role.SYSTEM_ADMIN)) {
            throw new SystemAdminRequiredException();
        }
    }
}
