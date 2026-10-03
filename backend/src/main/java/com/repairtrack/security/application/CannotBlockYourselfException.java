package com.repairtrack.security.application;

import com.repairtrack.common.error.ApplicationException;
import com.repairtrack.common.error.ErrorCategory;

/** An admin cannot lock themselves out. */
public class CannotBlockYourselfException extends ApplicationException {

    public CannotBlockYourselfException() {
        super(ErrorCategory.BUSINESS_RULE_VIOLATION, "CANNOT_BLOCK_YOURSELF", "You cannot block your own account.");
    }
}
