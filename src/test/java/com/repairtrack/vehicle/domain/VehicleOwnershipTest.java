package com.repairtrack.vehicle.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class VehicleOwnershipTest {

    private static final Instant NOW = Instant.parse("2026-09-25T10:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 25);

    @Test
    void ownershipCannotStartInTheFuture() {
        assertThatThrownBy(() -> VehicleOwnership.start(UUID.randomUUID(), UUID.randomUUID(), TODAY.plusDays(1),
                NOW, TODAY)).isInstanceOf(InvalidOwnershipPeriodException.class);
    }

    @Test
    void ownershipEndsWithinItsPeriod() {
        VehicleOwnership ownership = VehicleOwnership.start(UUID.randomUUID(), UUID.randomUUID(),
                TODAY.minusYears(2), NOW, TODAY);

        ownership.end(TODAY, NOW, TODAY);

        assertThat(ownership.isActive()).isFalse();
        assertThat(ownership.getEndDate()).isEqualTo(TODAY);
        assertThat(ownership.getStatus()).isEqualTo(OwnershipStatus.ENDED);
    }

    @Test
    void ownershipCannotEndBeforeItStartedOrInTheFuture() {
        VehicleOwnership ownership = VehicleOwnership.start(UUID.randomUUID(), UUID.randomUUID(),
                TODAY.minusDays(10), NOW, TODAY);

        assertThatThrownBy(() -> ownership.end(TODAY.minusDays(11), NOW, TODAY))
                .isInstanceOf(InvalidOwnershipPeriodException.class);
        assertThatThrownBy(() -> ownership.end(TODAY.plusDays(1), NOW, TODAY))
                .isInstanceOf(InvalidOwnershipPeriodException.class);
        assertThat(ownership.isActive()).isTrue();
    }
}
