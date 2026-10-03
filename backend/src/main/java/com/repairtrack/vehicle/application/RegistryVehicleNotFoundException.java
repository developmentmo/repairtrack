package com.repairtrack.vehicle.application;

import com.repairtrack.common.error.ApplicationException;
import com.repairtrack.common.error.ErrorCategory;

/** The vehicle registry does not know this plate. */
public class RegistryVehicleNotFoundException extends ApplicationException {

    public RegistryVehicleNotFoundException() {
        super(ErrorCategory.NOT_FOUND, "REGISTRY_VEHICLE_NOT_FOUND", "No registered vehicle with this license plate.");
    }
}
