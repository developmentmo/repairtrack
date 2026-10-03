package com.repairtrack.security.application;

import com.repairtrack.common.ratelimit.FixedWindowRateLimiter;

/**
 * Limits failed logins per (normalized) email address, regardless of IP, against password guessing from
 * many addresses. A successful login resets the counter. Unknown addresses are counted the same way, so the
 * answer never reveals whether an account exists.
 * <p>
 * Trade-off: someone who knows an email address can block that account's logins for one window. That is the
 * accepted cost of not letting passwords be guessed; the window is short.
 */
public class LoginThrottle {

    private final FixedWindowRateLimiter failures;

    public LoginThrottle(FixedWindowRateLimiter failures) {
        this.failures = failures;
    }

    /** @throws TooManyLoginAttemptsException when the limit of failed attempts is reached */
    public void checkNotBlocked(String normalizedEmail) {
        failures.retryAfterIfLimited(normalizedEmail).ifPresent(wait -> {
            throw new TooManyLoginAttemptsException(wait);
        });
    }

    public void recordFailure(String normalizedEmail) {
        failures.tryAcquire(normalizedEmail);
    }

    public void reset(String normalizedEmail) {
        failures.reset(normalizedEmail);
    }
}
