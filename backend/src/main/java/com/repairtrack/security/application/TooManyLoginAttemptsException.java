package com.repairtrack.security.application;

import java.time.Duration;

import com.repairtrack.common.error.ApplicationException;
import com.repairtrack.common.error.ErrorCategory;
import com.repairtrack.common.error.RetryAfterAware;

/** Too many failed logins for this email address. Same answer for existing and unknown addresses. */
public class TooManyLoginAttemptsException extends ApplicationException implements RetryAfterAware {

    private final Duration retryAfter;

    public TooManyLoginAttemptsException(Duration retryAfter) {
        super(ErrorCategory.TOO_MANY_REQUESTS, "TOO_MANY_LOGIN_ATTEMPTS",
                "Too many failed login attempts. Try again later.");
        this.retryAfter = retryAfter;
    }

    @Override
    public Duration retryAfter() {
        return retryAfter;
    }
}
