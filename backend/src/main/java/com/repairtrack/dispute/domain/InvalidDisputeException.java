package com.repairtrack.dispute.domain;

import com.repairtrack.common.error.ApplicationException;
import com.repairtrack.common.error.ErrorCategory;

public class InvalidDisputeException extends ApplicationException {

    public InvalidDisputeException(String message) {
        super(ErrorCategory.INVALID_REQUEST, "INVALID_DISPUTE_DATA", message);
    }
}
