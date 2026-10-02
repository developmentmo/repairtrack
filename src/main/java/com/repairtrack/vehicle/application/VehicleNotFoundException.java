package com.repairtrack.vehicle.application;

import com.repairtrack.common.error.ApplicationException;
import com.repairtrack.common.error.ErrorCategory;


public class VehicleNotFoundException extends ApplicationException {

    public VehicleNotFoundException() {
        super(ErrorCategory.NOT_FOUND, "VEHICLE_NOT_FOUND", "Vehicle not found.");
    }
}
