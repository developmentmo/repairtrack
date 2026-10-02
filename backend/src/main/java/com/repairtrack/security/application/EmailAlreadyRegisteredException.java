package com.repairtrack.security.application;

import com.repairtrack.common.error.ApplicationException;
import com.repairtrack.common.error.ErrorCategory;


public class EmailAlreadyRegisteredException extends ApplicationException {

    public EmailAlreadyRegisteredException() {
        super(ErrorCategory.CONFLICT, "EMAIL_ALREADY_REGISTERED", "An account with this email address already exists.");
    }
}
