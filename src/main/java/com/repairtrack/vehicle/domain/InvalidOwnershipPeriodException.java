package com.repairtrack.vehicle.domain;

import com.repairtrack.common.error.ApplicationException;
import com.repairtrack.common.error.ErrorCategory;

public class InvalidOwnershipPeriodException extends ApplicationException {

    public InvalidOwnershipPeriodException(String message) {
        super(ErrorCategory.BUSINESS_RULE_VIOLATION, "INVALID_OWNERSHIP_PERIOD", message);
    }
}
