package com.repairtrack.document.domain;

import com.repairtrack.common.error.ApplicationException;
import com.repairtrack.common.error.ErrorCategory;

public class UnsupportedFileTypeException extends ApplicationException {

    public UnsupportedFileTypeException() {
        super(ErrorCategory.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_FILE_TYPE", "Only PDF, JPEG and PNG files are accepted.");
    }
}
