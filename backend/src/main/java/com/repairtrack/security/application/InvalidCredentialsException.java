package com.repairtrack.security.application;

import com.repairtrack.common.error.ApplicationException;
import com.repairtrack.common.error.ErrorCategory;

/** Deliberately does not reveal whether the email exists. */
public class InvalidCredentialsException extends ApplicationException {

    public InvalidCredentialsException() {
        super(ErrorCategory.UNAUTHORIZED, "INVALID_CREDENTIALS", "Email or password is incorrect.");
    }
}
