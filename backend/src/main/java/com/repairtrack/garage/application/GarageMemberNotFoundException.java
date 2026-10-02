package com.repairtrack.garage.application;

import com.repairtrack.common.error.ApplicationException;
import com.repairtrack.common.error.ErrorCategory;

public class GarageMemberNotFoundException extends ApplicationException {

    public GarageMemberNotFoundException() {
        super(ErrorCategory.NOT_FOUND, "GARAGE_MEMBER_NOT_FOUND", "This user is not a member of the garage.");
    }
}
