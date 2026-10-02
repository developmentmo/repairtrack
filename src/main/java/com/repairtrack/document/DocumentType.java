package com.repairtrack.document;

public enum DocumentType {
    INVOICE(true),
    WORK_ORDER(true),
    INSPECTION_REPORT(true),
    PHOTO(false),
    OTHER(false);

    private final boolean evidence;

    DocumentType(boolean evidence) {
        this.evidence = evidence;
    }

    /**
     * Whether this kind of document substantiates that the work was done, and therefore raises an
     * owner record to DOCUMENTED. A photo or "other" file does not.
     */
    public boolean isEvidence() {
        return evidence;
    }
}
