package com.repairtrack.security.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.repairtrack.security.Role;

class UserTest {

    private static final Instant NOW = Instant.parse("2026-09-25T10:00:00Z");

    @Test
    void registeredUserIsActiveOwnerOnlyWithNormalizedEmail() {
        User user = User.register("  Jan.Jansen@Example.NL ", "{bcrypt}hash", " Jan ", "Jansen", NOW);

        assertThat(user.getId()).isNotNull();
        assertThat(user.getEmail()).isEqualTo("jan.jansen@example.nl");
        assertThat(user.getFirstName()).isEqualTo("Jan");
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getRoles()).containsExactly(Role.OWNER);
        assertThat(user.getCreatedAt()).isEqualTo(NOW);
        assertThat(user.isActive()).isTrue();
    }

    @Test
    void rolesCannotBeModifiedThroughGetter() {
        User user = User.register("a@example.com", "{bcrypt}hash", "A", "B", NOW);

        assertThatThrownBy(() -> user.getRoles().add(Role.SYSTEM_ADMIN))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void blockedUserIsNotActive() {
        User user = User.register("a@example.com", "{bcrypt}hash", "A", "B", NOW);

        user.block(NOW.plusSeconds(60));

        assertThat(user.isActive()).isFalse();
        assertThat(user.getStatus()).isEqualTo(UserStatus.BLOCKED);
        assertThat(user.getUpdatedAt()).isEqualTo(NOW.plusSeconds(60));
    }

    @Test
    void blankNamesAreRejected() {
        assertThatThrownBy(() -> User.register("a@example.com", "{bcrypt}hash", " ", "B", NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
