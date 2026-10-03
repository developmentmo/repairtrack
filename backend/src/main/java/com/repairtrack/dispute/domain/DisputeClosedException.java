package com.repairtrack.dispute.domain;

import com.repairtrack.common.error.ApplicationException;
import com.repairtrack.common.error.ErrorCategory;

/** The dispute is decided, or the step is no longer possible in its current phase. */
public class DisputeClosedException extends ApplicationException {

    public DisputeClosedException(String message) {
        super(ErrorCategory.CONFLICT, "DISPUTE_CLOSED", message);
    }
}
