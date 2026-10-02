package com.repairtrack.garage.domain;

import com.repairtrack.common.error.ApplicationException;
import com.repairtrack.common.error.ErrorCategory;
import com.repairtrack.garage.GarageVerificationStatus;

public class InvalidVerificationTransitionException extends ApplicationException {

    public InvalidVerificationTransitionException(GarageVerificationStatus from, GarageVerificationStatus to) {
        super(ErrorCategory.BUSINESS_RULE_VIOLATION, "INVALID_VERIFICATION_TRANSITION",
                "Verification status cannot change from " + from + " to " + to + ".");
    }
}
