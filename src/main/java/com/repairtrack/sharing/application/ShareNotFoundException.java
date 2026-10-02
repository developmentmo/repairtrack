package com.repairtrack.sharing.application;

import com.repairtrack.common.error.ApplicationException;
import com.repairtrack.common.error.ErrorCategory;

/**
 * Deliberately one answer for unknown, revoked, expired and ownership-changed links: the public API
 * reveals nothing about which links exist or why one stopped working.
 */
public class ShareNotFoundException extends ApplicationException {

    public ShareNotFoundException() {
        super(ErrorCategory.NOT_FOUND, "SHARE_NOT_FOUND", "This link is invalid or no longer valid.");
    }
}
