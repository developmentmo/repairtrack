package com.repairtrack.document.infrastructure;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/** {@code /actuator/health} component "storage": the document bucket is reachable with our credentials. */
@Component("storageHealthIndicator")
class StorageHealthIndicator implements HealthIndicator {

    private final DocumentStorage storage;

    StorageHealthIndicator(DocumentStorage storage) {
        this.storage = storage;
    }

    @Override
    public Health health() {
        try {
            storage.checkAvailable();
            return Health.up().build();
        } catch (RuntimeException e) {
            return Health.down().withDetail("error", e.getClass().getSimpleName()).build();
        }
    }
}
