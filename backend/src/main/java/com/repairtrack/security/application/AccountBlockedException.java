package com.repairtrack.security.application;

import com.repairtrack.common.error.ApplicationException;
import com.repairtrack.common.error.ErrorCategory;


public class AccountBlockedException extends ApplicationException {

    public AccountBlockedException() {
        super(ErrorCategory.FORBIDDEN, "ACCOUNT_BLOCKED", "This account has been blocked.");
    }
}
