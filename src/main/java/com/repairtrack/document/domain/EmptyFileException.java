package com.repairtrack.document.domain;

import com.repairtrack.common.error.ApplicationException;
import com.repairtrack.common.error.ErrorCategory;

public class EmptyFileException extends ApplicationException {

    public EmptyFileException() {
        super(ErrorCategory.INVALID_REQUEST, "EMPTY_FILE", "The uploaded file is empty.");
    }
}
