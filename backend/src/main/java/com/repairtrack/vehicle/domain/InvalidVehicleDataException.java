package com.repairtrack.vehicle.domain;

import com.repairtrack.common.error.ApplicationException;
import com.repairtrack.common.error.ErrorCategory;

/** Domain-level input validation; a second line of defence behind request DTO validation. */
public class InvalidVehicleDataException extends ApplicationException {

    public InvalidVehicleDataException(String message) {
        super(ErrorCategory.INVALID_REQUEST, "INVALID_VEHICLE_DATA", message);
    }
}
