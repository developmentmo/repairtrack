package com.repairtrack.sharing.domain;

import com.repairtrack.common.error.ApplicationException;
import com.repairtrack.common.error.ErrorCategory;

public class InvalidShareException extends ApplicationException {

    public InvalidShareException(String message) {
        super(ErrorCategory.INVALID_REQUEST, "INVALID_SHARE", message);
    }
}
