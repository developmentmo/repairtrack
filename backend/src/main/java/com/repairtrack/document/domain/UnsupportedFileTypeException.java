package com.repairtrack.document.domain;

import com.repairtrack.common.error.ApplicationException;
import com.repairtrack.common.error.ErrorCategory;

public class UnsupportedFileTypeException extends ApplicationException {

    public UnsupportedFileTypeException() {
        this("Only PDF, JPEG and PNG files are accepted.");
    }

    public UnsupportedFileTypeException(String message) {
        super(ErrorCategory.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_FILE_TYPE", message);
    }
}
