package com.repairtrack.garage.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.repairtrack.garage.GarageRole;

class GarageUserTest {

    private static final Instant NOW = Instant.parse("2026-09-25T10:00:00Z");

    @Test
    void endingKeepsTheRowAsHistory() {
        GarageUser membership = GarageUser.add(UUID.randomUUID(), UUID.randomUUID(), GarageRole.MECHANIC,
                UUID.randomUUID(), NOW);
        UUID remover = UUID.randomUUID();

        membership.end(remover, NOW.plusSeconds(60));

        assertThat(membership.isActive()).isFalse();
        assertThat(membership.getStatus()).isEqualTo(MembershipStatus.ENDED);
        assertThat(membership.getEndedAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(membership.getRole()).isEqualTo(GarageRole.MECHANIC);
    }

    @Test
    void membershipCannotBeEndedTwice() {
        GarageUser membership = GarageUser.add(UUID.randomUUID(), UUID.randomUUID(), GarageRole.MECHANIC,
                UUID.randomUUID(), NOW);
        membership.end(UUID.randomUUID(), NOW);

        assertThatThrownBy(() -> membership.end(UUID.randomUUID(), NOW)).isInstanceOf(IllegalStateException.class);
    }
}
