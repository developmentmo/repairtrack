package com.repairtrack.sharing.application;

import com.repairtrack.common.error.ApplicationException;
import com.repairtrack.common.error.ErrorCategory;

public class VehicleUnderDisputeException extends ApplicationException {

    public VehicleUnderDisputeException() {
        super(ErrorCategory.CONFLICT, "VEHICLE_UNDER_DISPUTE",
                "The ownership of this vehicle is disputed; no new share links until it is decided.");
    }
}
