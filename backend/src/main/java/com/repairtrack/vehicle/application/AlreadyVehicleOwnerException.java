package com.repairtrack.vehicle.application;

import com.repairtrack.common.error.ApplicationException;
import com.repairtrack.common.error.ErrorCategory;


public class AlreadyVehicleOwnerException extends ApplicationException {

    public AlreadyVehicleOwnerException() {
        super(ErrorCategory.CONFLICT, "ALREADY_VEHICLE_OWNER", "You are already the owner of this vehicle.");
    }
}
