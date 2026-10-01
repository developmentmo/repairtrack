package com.repairtrack.repair.application;

import com.repairtrack.common.error.ApplicationException;
import com.repairtrack.common.error.ErrorCategory;

public class RepairNotFoundException extends ApplicationException {

    public RepairNotFoundException() {
        super(ErrorCategory.NOT_FOUND, "REPAIR_NOT_FOUND", "Repair record not found.");
    }
}
