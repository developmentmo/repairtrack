package com.repairtrack.garage.application;

import com.repairtrack.common.error.ApplicationException;
import com.repairtrack.common.error.ErrorCategory;

public class LastGarageAdminException extends ApplicationException {

    public LastGarageAdminException() {
        super(ErrorCategory.BUSINESS_RULE_VIOLATION, "LAST_GARAGE_ADMIN", "A garage must keep at least one garage admin.");
    }
}
