package com.repairtrack.security.application;

import com.repairtrack.common.error.ApplicationException;
import com.repairtrack.common.error.ErrorCategory;

/** Only system administrators may do this. */
public class SystemAdminRequiredException extends ApplicationException {

    public SystemAdminRequiredException() {
        super(ErrorCategory.FORBIDDEN, "SYSTEM_ADMIN_REQUIRED", "Only system administrators may do this.");
    }
}
