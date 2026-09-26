package com.repairtrack.vehicle.application;

import com.repairtrack.common.error.ApplicationException;
import com.repairtrack.common.error.ErrorCategory;


public class InvalidVehicleSearchException extends ApplicationException {

    public InvalidVehicleSearchException() {
        super(ErrorCategory.INVALID_REQUEST, "INVALID_VEHICLE_SEARCH", "Search by exactly one of: vin, licensePlate.");
    }
}
