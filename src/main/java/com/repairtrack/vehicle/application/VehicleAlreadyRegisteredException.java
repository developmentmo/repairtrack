package com.repairtrack.vehicle.application;

import com.repairtrack.common.error.ApplicationException;
import com.repairtrack.common.error.ErrorCategory;


public class VehicleAlreadyRegisteredException extends ApplicationException {

    public VehicleAlreadyRegisteredException() {
        super(ErrorCategory.CONFLICT, "VEHICLE_ALREADY_REGISTERED", "A vehicle with this VIN is already registered. Search for it by VIN and claim it instead.");
    }
}
