package com.repairtrack.common.error;

/**
 * Semantic category of a business error. {@link GlobalExceptionHandler} maps each category
 * to exactly one HTTP status, so modules never deal with HTTP themselves.
 */
public enum ErrorCategory {

    /** Input is syntactically valid but violates a rule (400). */
    INVALID_REQUEST,

    /** Caller is not authenticated or credentials/tokens are invalid (401). */
    UNAUTHORIZED,

    /** Caller is authenticated but not allowed to do this (403). */
    FORBIDDEN,

    /** Referenced resource does not exist or is not visible to the caller (404). */
    NOT_FOUND,

    /** Request conflicts with current state, e.g. a duplicate (409). */
    CONFLICT,

    /** Request is well-formed but breaks a domain rule (422). */
    BUSINESS_RULE_VIOLATION,

    /** Uploaded content is too large (413). */
    PAYLOAD_TOO_LARGE,

    /** Uploaded content is of a type we do not accept (415). */
    UNSUPPORTED_MEDIA_TYPE
}
