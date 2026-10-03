package com.repairtrack.vehicle.application;

import com.repairtrack.common.error.ApplicationException;
import com.repairtrack.common.error.ErrorCategory;

/** The contested ownership is no longer the active one (ended or transferred in the meantime). */
public class OwnershipChangedException extends ApplicationException {

    public OwnershipChangedException() {
        super(ErrorCategory.CONFLICT, "OWNERSHIP_CHANGED",
                "The vehicle's ownership changed in the meantime; this dispute can no longer be upheld.");
    }
}
