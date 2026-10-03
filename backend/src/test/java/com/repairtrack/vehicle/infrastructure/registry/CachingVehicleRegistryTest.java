package com.repairtrack.vehicle.infrastructure.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class CachingVehicleRegistryTest {

    private Instant now = Instant.parse("2026-10-03T10:00:00Z");
    private final List<String> calls = new ArrayList<>();
    private boolean failing;

    private final VehicleRegistry delegate = plate -> {
        calls.add(plate);
        if (failing) {
            throw new VehicleRegistryUnavailableException(null);
        }
        return plate.startsWith("ZZ") ? Optional.empty()
                : Optional.of(new RegistryVehicle(plate, "VOLVO", "V60", null, null, null, null, null));
    };

    private final CachingVehicleRegistry cache = new CachingVehicleRegistry(delegate, Duration.ofHours(24), 3,
            new Clock() {
                @Override
                public ZoneOffset getZone() {
                    return ZoneOffset.UTC;
                }

                @Override
                public Clock withZone(ZoneId zone) {
                    return this;
                }

                @Override
                public Instant instant() {
                    return now;
                }
            });

    @Test
    void reusesAnswersIncludingUnknownPlatesUntilTheyExpire() {
        cache.lookup("12ABC3");
        cache.lookup("12ABC3");
        cache.lookup("ZZ999Z");
        assertThat(cache.lookup("ZZ999Z")).isEmpty();
        assertThat(calls).containsExactly("12ABC3", "ZZ999Z");

        now = now.plus(Duration.ofHours(24));
        cache.lookup("12ABC3");
        assertThat(calls).containsExactly("12ABC3", "ZZ999Z", "12ABC3");
    }

    @Test
    void doesNotCacheFailures() {
        failing = true;
        assertThatThrownBy(() -> cache.lookup("12ABC3")).isInstanceOf(VehicleRegistryUnavailableException.class);

        failing = false;
        assertThat(cache.lookup("12ABC3")).isPresent();
        assertThat(calls).containsExactly("12ABC3", "12ABC3");
    }

    @Test
    void staysWithinItsMaximumSize() {
        for (int i = 0; i < 10; i++) {
            cache.lookup("PLATE" + i);
        }
        assertThat(cache.size()).isLessThanOrEqualTo(3);
    }
}
