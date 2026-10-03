package com.repairtrack.vehicle.infrastructure.registry;

import com.repairtrack.common.error.ApplicationException;
import com.repairtrack.common.error.ErrorCategory;

/** The registry is switched off or gave no usable answer; the client fills in the details by hand. */
public class VehicleRegistryUnavailableException extends ApplicationException {

    public VehicleRegistryUnavailableException(Throwable cause) {
        super(ErrorCategory.SERVICE_UNAVAILABLE, "REGISTRY_UNAVAILABLE",
                "Vehicle registry data is not available right now. Enter the details yourself.");
        if (cause != null) {
            initCause(cause);
        }
    }
}
