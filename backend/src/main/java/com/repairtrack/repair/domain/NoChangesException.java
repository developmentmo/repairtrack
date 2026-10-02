package com.repairtrack.repair.domain;

import com.repairtrack.common.error.ApplicationException;
import com.repairtrack.common.error.ErrorCategory;

public class NoChangesException extends ApplicationException {

    public NoChangesException() {
        super(ErrorCategory.INVALID_REQUEST, "NO_CHANGES", "The correction does not change anything.");
    }
}
