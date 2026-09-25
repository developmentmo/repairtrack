package com.repairtrack.security.application;

import com.repairtrack.common.error.ApplicationException;
import com.repairtrack.common.error.ErrorCategory;


public class UserNotFoundException extends ApplicationException {

    public UserNotFoundException() {
        super(ErrorCategory.NOT_FOUND, "USER_NOT_FOUND", "User not found.");
    }
}
