package com.repairtrack.vehicle.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.repairtrack.vehicle.application.VehicleRegistryLookup;

/**
 * {@code GET /api/v1/vehicle-registry/{licensePlate}}: public RDW data for any signed-in user (the same data is
 * public at the RDW). 404 {@code REGISTRY_VEHICLE_NOT_FOUND}, 503 {@code REGISTRY_UNAVAILABLE}.
 */
@RestController
class VehicleRegistryController {

    private final VehicleRegistryLookup lookup;

    VehicleRegistryController(VehicleRegistryLookup lookup) {
        this.lookup = lookup;
    }

    @GetMapping("/api/v1/vehicle-registry/{licensePlate}")
    RegistryVehicleResponse lookup(@PathVariable("licensePlate") String licensePlate) {
        return RegistryVehicleResponse.from(lookup.lookup(licensePlate));
    }
}
