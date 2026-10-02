package com.repairtrack.garage.application;

import com.repairtrack.common.error.ApplicationException;
import com.repairtrack.common.error.ErrorCategory;

public class GarageNotFoundException extends ApplicationException {

    public GarageNotFoundException() {
        super(ErrorCategory.NOT_FOUND, "GARAGE_NOT_FOUND", "Garage not found.");
    }
}
