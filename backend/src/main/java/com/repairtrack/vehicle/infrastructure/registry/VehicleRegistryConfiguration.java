package com.repairtrack.vehicle.infrastructure.registry;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tools.jackson.databind.json.JsonMapper;

/** Chooses the registry from {@code repairtrack.vehicle-registry.mode}. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(VehicleRegistryProperties.class)
class VehicleRegistryConfiguration {

    private static final Logger log = LoggerFactory.getLogger(VehicleRegistryConfiguration.class);

    @Bean
    VehicleRegistry vehicleRegistry(VehicleRegistryProperties properties, JsonMapper jsonMapper, Clock clock) {
        if (!properties.enabled()) {
            log.info("Vehicle registry lookups are disabled (repairtrack.vehicle-registry.mode=disabled)");
            return licensePlate -> {
                throw new VehicleRegistryUnavailableException(null);
            };
        }
        return new CachingVehicleRegistry(new RdwOpenDataRegistry(properties, jsonMapper),
                properties.cacheTtl(), properties.cacheSize(), clock);
    }
}
