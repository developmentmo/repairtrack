package com.repairtrack.repair.domain;

import com.repairtrack.common.error.ApplicationException;
import com.repairtrack.common.error.ErrorCategory;

public class InvalidRepairDataException extends ApplicationException {

    public InvalidRepairDataException(String message) {
        super(ErrorCategory.INVALID_REQUEST, "INVALID_REPAIR_DATA", message);
    }
}
