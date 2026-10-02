package com.repairtrack.garage.application;

import com.repairtrack.common.error.ApplicationException;
import com.repairtrack.common.error.ErrorCategory;

public class GarageAccessDeniedException extends ApplicationException {

    public GarageAccessDeniedException(String message) {
        super(ErrorCategory.FORBIDDEN, "GARAGE_ACCESS_DENIED", message);
    }
}
