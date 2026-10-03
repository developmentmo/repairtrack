package com.repairtrack.security.application;

import com.repairtrack.common.error.ApplicationException;
import com.repairtrack.common.error.ErrorCategory;

/** Login is refused until the email address has been confirmed. Only after a correct password. */
public class EmailNotVerifiedException extends ApplicationException {

    public EmailNotVerifiedException() {
        super(ErrorCategory.FORBIDDEN, "EMAIL_NOT_VERIFIED", "The email address has not been confirmed yet.");
    }
}
