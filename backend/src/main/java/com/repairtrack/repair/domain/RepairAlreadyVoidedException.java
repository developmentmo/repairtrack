package com.repairtrack.repair.domain;

import com.repairtrack.common.error.ApplicationException;
import com.repairtrack.common.error.ErrorCategory;

public class RepairAlreadyVoidedException extends ApplicationException {

    public RepairAlreadyVoidedException() {
        super(ErrorCategory.BUSINESS_RULE_VIOLATION, "REPAIR_ALREADY_VOIDED", "This record has been voided and can no longer be changed.");
    }
}
