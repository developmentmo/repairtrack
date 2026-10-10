package com.repairtrack.security.application;

import com.repairtrack.common.error.ApplicationException;
import com.repairtrack.common.error.ErrorCategory;

/** The login session ended because it was idle for longer than the idle timeout. The user has to log in again. */
public class SessionExpiredException extends ApplicationException {

    public SessionExpiredException() {
        super(ErrorCategory.UNAUTHORIZED, "SESSION_EXPIRED", "The session has expired due to inactivity.");
    }
}
