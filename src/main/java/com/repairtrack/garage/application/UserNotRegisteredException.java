package com.repairtrack.garage.application;

import com.repairtrack.common.error.ApplicationException;
import com.repairtrack.common.error.ErrorCategory;

public class UserNotRegisteredException extends ApplicationException {

    public UserNotRegisteredException() {
        super(ErrorCategory.NOT_FOUND, "USER_NOT_FOUND", "No active RepairTrack account exists for this email address.");
    }
}
