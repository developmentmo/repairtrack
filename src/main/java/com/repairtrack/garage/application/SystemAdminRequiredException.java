package com.repairtrack.garage.application;

import com.repairtrack.common.error.ApplicationException;
import com.repairtrack.common.error.ErrorCategory;

public class SystemAdminRequiredException extends ApplicationException {

    public SystemAdminRequiredException() {
        super(ErrorCategory.FORBIDDEN, "SYSTEM_ADMIN_REQUIRED", "Only RepairTrack administrators can perform this action.");
    }
}
