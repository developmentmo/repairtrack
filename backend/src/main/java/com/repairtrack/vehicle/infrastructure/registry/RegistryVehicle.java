package com.repairtrack.vehicle.infrastructure.registry;

import java.time.LocalDate;
import java.util.List;

/**
 * Public data about a registered vehicle. Contains no personal data (the RDW open data has no owner details).
 * Every field except {@code licensePlate} may be null when the registry does not provide it.
 *
 * @param make                  e.g. "VOLKSWAGEN"
 * @param model                 trade name, e.g. "GOLF"
 * @param vehicleType           e.g. "Personenauto"
 * @param firstRegistrationDate first admission anywhere (for imports: abroad)
 * @param apkExpiryDate         expiry of the Dutch periodic inspection (APK)
 * @param primaryColor          e.g. "GRIJS"
 * @param fuelTypes             e.g. ["Benzine", "Elektriciteit"] for a hybrid; empty when unknown
 */
public record RegistryVehicle(
        String licensePlate,
        String make,
        String model,
        String vehicleType,
        LocalDate firstRegistrationDate,
        LocalDate apkExpiryDate,
        String primaryColor,
        List<String> fuelTypes
) {

    public RegistryVehicle {
        fuelTypes = fuelTypes == null ? List.of() : List.copyOf(fuelTypes);
    }
}
