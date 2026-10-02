package com.repairtrack.garage.application;

import com.repairtrack.common.error.ApplicationException;
import com.repairtrack.common.error.ErrorCategory;

public class AlreadyGarageMemberException extends ApplicationException {

    public AlreadyGarageMemberException() {
        super(ErrorCategory.CONFLICT, "ALREADY_GARAGE_MEMBER", "This user is already a member of the garage.");
    }
}
