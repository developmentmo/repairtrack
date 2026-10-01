package com.repairtrack.repair.application;

import com.repairtrack.common.error.ApplicationException;
import com.repairtrack.common.error.ErrorCategory;

public class RepairAccessDeniedException extends ApplicationException {

    public RepairAccessDeniedException(String message) {
        super(ErrorCategory.FORBIDDEN, "REPAIR_ACCESS_DENIED", message);
    }
}
