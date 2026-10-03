package com.repairtrack.common.error;

import java.time.Duration;

/** An {@link ApplicationException} after which the client should wait; sent as the {@code Retry-After} header. */
public interface RetryAfterAware {

    Duration retryAfter();

    /** Whole seconds, at least 1, as HTTP expects. */
    static long seconds(Duration retryAfter) {
        return Math.max(1, (retryAfter.toMillis() + 999) / 1000);
    }
}
