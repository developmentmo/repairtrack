package com.repairtrack.sharing.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class VehicleShareTest {

    private static final Instant NOW = Instant.parse("2026-10-02T10:00:00Z");
    private static final String HASH = "a".repeat(64);

    @Test
    void newShareIsActiveUntilItExpires() {
        VehicleShare share = share(Duration.ofDays(30));

        assertThat(share.status(NOW, true)).isEqualTo(ShareStatus.ACTIVE);
        assertThat(share.status(NOW.plus(Duration.ofDays(30)).minusSeconds(1), true)).isEqualTo(ShareStatus.ACTIVE);
        assertThat(share.status(NOW.plus(Duration.ofDays(30)), true)).isEqualTo(ShareStatus.EXPIRED);
    }

    @Test
    void revokedShareStaysRevokedAndRevokeIsIdempotent() {
        VehicleShare share = share(Duration.ofDays(30));
        UUID owner = UUID.randomUUID();

        assertThat(share.revoke(owner, NOW)).isTrue();
        assertThat(share.revoke(owner, NOW.plusSeconds(5))).isFalse();
        assertThat(share.getRevokedAt()).isEqualTo(NOW);
        assertThat(share.status(NOW, true)).isEqualTo(ShareStatus.REVOKED);
    }

    @Test
    void shareStopsWorkingWhenItsCreatorNoLongerOwnsTheVehicle() {
        assertThat(share(Duration.ofDays(30)).status(NOW, false)).isEqualTo(ShareStatus.OWNER_CHANGED);
    }

    @Test
    void validityMustBeBetweenOneDayAndOneYear() {
        assertThatThrownBy(() -> share(Duration.ofHours(23))).isInstanceOf(InvalidShareException.class);
        assertThatThrownBy(() -> share(Duration.ofDays(366))).isInstanceOf(InvalidShareException.class);
        share(Duration.ofDays(1));
        share(Duration.ofDays(365));
    }

    private static VehicleShare share(Duration validity) {
        return VehicleShare.create(UUID.randomUUID(), HASH, false, validity, UUID.randomUUID(), NOW);
    }
}
