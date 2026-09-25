package com.repairtrack.garage.application;

import com.repairtrack.common.error.ApplicationException;
import com.repairtrack.common.error.ErrorCategory;

public class GarageSuspendedException extends ApplicationException {

    public GarageSuspendedException() {
        super(ErrorCategory.FORBIDDEN, "GARAGE_SUSPENDED", "This garage is suspended and cannot record work.");
    }
}
