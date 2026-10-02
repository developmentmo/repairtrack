package com.repairtrack;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Fails the build when a module reaches into another module's internals or when
 * modules form a dependency cycle. Runs without a Spring context.
 */
class ModularityTest {

    private final ApplicationModules modules = ApplicationModules.of(RepairTrackApplication.class);

    @Test
    void moduleBoundariesAreRespected() {
        modules.verify();
    }
}
