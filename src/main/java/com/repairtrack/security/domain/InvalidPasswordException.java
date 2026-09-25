package com.repairtrack.security.domain;

import com.repairtrack.common.error.ApplicationException;
import com.repairtrack.common.error.ErrorCategory;

public class InvalidPasswordException extends ApplicationException {

    public InvalidPasswordException(String message) {
        super(ErrorCategory.INVALID_REQUEST, "INVALID_PASSWORD", message);
    }
}
