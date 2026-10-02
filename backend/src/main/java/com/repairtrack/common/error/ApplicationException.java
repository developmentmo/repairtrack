package com.repairtrack.common.error;

import java.util.Objects;

/**
 * Base class for expected, client-facing business errors.
 * <p>
 * Contract: {@link #getMessage()} is shown to API clients, so it must never contain
 * internal details, stack information or other users' data. {@link #code()} is a stable,
 * machine-readable identifier (e.g. {@code EMAIL_ALREADY_REGISTERED}).
 * <p>
 * Each module defines its own concrete subclasses; this class only carries the category and code.
 */
public abstract class ApplicationException extends RuntimeException {

    private final ErrorCategory category;
    private final String code;

    protected ApplicationException(ErrorCategory category, String code, String message) {
        super(message);
        this.category = Objects.requireNonNull(category, "category");
        this.code = Objects.requireNonNull(code, "code");
    }

    public ErrorCategory category() {
        return category;
    }

    public String code() {
        return code;
    }
}
