package com.repairtrack.dispute.domain;

import com.repairtrack.common.error.ApplicationException;
import com.repairtrack.common.error.ErrorCategory;

/** The current owner can still respond; a decision has to wait until they did or the deadline passed. */
public class DisputeNotReviewableException extends ApplicationException {

    public DisputeNotReviewableException() {
        super(ErrorCategory.CONFLICT, "DISPUTE_NOT_REVIEWABLE",
                "The current owner can still respond to this dispute.");
    }
}
