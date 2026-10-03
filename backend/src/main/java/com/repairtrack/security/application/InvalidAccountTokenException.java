package com.repairtrack.security.application;

import com.repairtrack.common.error.ApplicationException;
import com.repairtrack.common.error.ErrorCategory;

/** Unknown, expired or already used link. One answer for all cases. */
public class InvalidAccountTokenException extends ApplicationException {

    public InvalidAccountTokenException() {
        super(ErrorCategory.INVALID_REQUEST, "INVALID_TOKEN", "This link is invalid or has expired.");
    }
}
