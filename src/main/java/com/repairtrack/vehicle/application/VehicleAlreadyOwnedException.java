package com.repairtrack.vehicle.application;

import com.repairtrack.common.error.ApplicationException;
import com.repairtrack.common.error.ErrorCategory;


public class VehicleAlreadyOwnedException extends ApplicationException {

    public VehicleAlreadyOwnedException() {
        super(ErrorCategory.CONFLICT, "VEHICLE_ALREADY_OWNED", "This vehicle currently has a registered owner. The current owner must end their ownership first.");
    }
}
