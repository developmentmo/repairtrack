package com.repairtrack.security.application;

import com.repairtrack.common.error.ApplicationException;
import com.repairtrack.common.error.ErrorCategory;

/** Re-authentication for a sensitive action failed. Not 401: the session itself is still valid. */
public class PasswordIncorrectException extends ApplicationException {

    public PasswordIncorrectException() {
        super(ErrorCategory.FORBIDDEN, "PASSWORD_INCORRECT", "The password is incorrect.");
    }
}
