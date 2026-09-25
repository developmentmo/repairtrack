package com.repairtrack.security.application;

import com.repairtrack.common.error.ApplicationException;
import com.repairtrack.common.error.ErrorCategory;

/** Covers unknown, expired, revoked and replayed refresh tokens alike. */
public class InvalidRefreshTokenException extends ApplicationException {

    public InvalidRefreshTokenException() {
        super(ErrorCategory.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "The refresh token is invalid or has expired.");
    }
}
