package com.repairtrack.common.error;

import java.time.Instant;

/**
 * Uniform error body returned by every REST endpoint.
 *
 * @param timestamp moment the error was produced (UTC)
 * @param status    HTTP status code
 * @param code      stable, machine-readable error code (e.g. {@code INVALID_MILEAGE}); clients branch on this
 * @param message   human-readable explanation; never contains stack traces or internal details
 * @param path      request path that produced the error
 */
public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path
) {
}
