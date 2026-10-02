package com.repairtrack.vehicle.application;

import com.repairtrack.common.error.ApplicationException;
import com.repairtrack.common.error.ErrorCategory;

public class VehicleAccessDeniedException extends ApplicationException {

    public VehicleAccessDeniedException(String message) {
        super(ErrorCategory.FORBIDDEN, "VEHICLE_ACCESS_DENIED", message);
    }
}
