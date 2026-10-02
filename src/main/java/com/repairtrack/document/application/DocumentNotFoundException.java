package com.repairtrack.document.application;

import com.repairtrack.common.error.ApplicationException;
import com.repairtrack.common.error.ErrorCategory;

public class DocumentNotFoundException extends ApplicationException {

    public DocumentNotFoundException() {
        super(ErrorCategory.NOT_FOUND, "DOCUMENT_NOT_FOUND", "Document not found.");
    }
}
