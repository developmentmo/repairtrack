package com.repairtrack.document.domain;

import com.repairtrack.common.error.ApplicationException;
import com.repairtrack.common.error.ErrorCategory;

public class FileTooLargeException extends ApplicationException {

    public FileTooLargeException() {
        super(ErrorCategory.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "The uploaded file is too large.");
    }
}
