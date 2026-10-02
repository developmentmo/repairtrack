package com.repairtrack.garage.domain;

import com.repairtrack.common.error.ApplicationException;
import com.repairtrack.common.error.ErrorCategory;

/** Domain-level input validation; a second line of defence behind request DTO validation. */
public class InvalidGarageDataException extends ApplicationException {

    public InvalidGarageDataException(String message) {
        super(ErrorCategory.INVALID_REQUEST, "INVALID_GARAGE_DATA", message);
    }
}
