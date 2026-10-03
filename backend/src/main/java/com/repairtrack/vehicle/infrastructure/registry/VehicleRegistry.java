package com.repairtrack.vehicle.infrastructure.registry;

import java.util.Optional;

/**
 * An external source of public vehicle data by license plate (in the Netherlands: RDW Open Data).
 * <p>
 * The result is a <em>suggestion</em> for filling in a form. It proves nothing about ownership and never changes a
 * vehicle's provenance or verification; the VIN stays the vehicle's identity.
 */
public interface VehicleRegistry {

    /**
     * @param licensePlate a normalized plate (uppercase letters and digits, see {@code Vehicle.normalizeLicensePlate})
     * @return the registered vehicle, or empty when the registry does not know the plate
     * @throws VehicleRegistryUnavailableException when the registry is switched off, slow or failing
     */
    Optional<RegistryVehicle> lookup(String licensePlate);
}
