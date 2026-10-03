package com.repairtrack.vehicle.infrastructure.registry;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remembers answers (including "unknown plate") for a while, so repeated lookups while filling in a form, or by
 * several users, do not hit the registry again. Failures are not cached. In-memory and per instance, like the
 * rate limiter.
 */
final class CachingVehicleRegistry implements VehicleRegistry {

    private record Entry(Optional<RegistryVehicle> vehicle, Instant expiresAt) {
    }

    private final VehicleRegistry delegate;
    private final Duration ttl;
    private final int maxSize;
    private final Clock clock;
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    CachingVehicleRegistry(VehicleRegistry delegate, Duration ttl, int maxSize, Clock clock) {
        this.delegate = delegate;
        this.ttl = ttl;
        this.maxSize = maxSize;
        this.clock = clock;
    }

    @Override
    public Optional<RegistryVehicle> lookup(String licensePlate) {
        Instant now = clock.instant();
        Entry cached = entries.get(licensePlate);
        if (cached != null && now.isBefore(cached.expiresAt())) {
            return cached.vehicle();
        }
        Optional<RegistryVehicle> vehicle = delegate.lookup(licensePlate);
        makeRoom(now);
        entries.put(licensePlate, new Entry(vehicle, now.plus(ttl)));
        return vehicle;
    }

    int size() {
        return entries.size();
    }

    private void makeRoom(Instant now) {
        if (entries.size() < maxSize) {
            return;
        }
        entries.values().removeIf(entry -> !now.isBefore(entry.expiresAt()));
        if (entries.size() >= maxSize) {
            entries.clear();
        }
    }
}
