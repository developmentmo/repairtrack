package com.repairtrack.vehicle.application;

import org.springframework.stereotype.Service;

import com.repairtrack.vehicle.domain.InvalidVehicleDataException;
import com.repairtrack.vehicle.domain.Vehicle;
import com.repairtrack.vehicle.infrastructure.registry.RegistryVehicle;
import com.repairtrack.vehicle.infrastructure.registry.VehicleRegistry;

/**
 * Public registry data for a license plate, to pre-fill the "register vehicle" form. Read-only: nothing is stored,
 * and the data never counts as proof of anything (ownership, provenance, verification).
 */
@Service
public class VehicleRegistryLookup {

    private final VehicleRegistry registry;

    VehicleRegistryLookup(VehicleRegistry registry) {
        this.registry = registry;
    }

    public RegistryVehicle lookup(String licensePlate) {
        String normalized = Vehicle.normalizeLicensePlate(licensePlate);
        if (normalized == null) {
            throw new InvalidVehicleDataException("License plate is required.");
        }
        return registry.lookup(normalized).orElseThrow(RegistryVehicleNotFoundException::new);
    }
}
