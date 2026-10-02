package com.repairtrack.vehicle.application;

import com.repairtrack.common.error.ApplicationException;
import com.repairtrack.common.error.ErrorCategory;

/** The claimant could not prove access to the vehicle's documents (VIN mismatch). */
public class OwnershipProofInvalidException extends ApplicationException {

    public OwnershipProofInvalidException() {
        super(ErrorCategory.FORBIDDEN, "OWNERSHIP_PROOF_INVALID", "The VIN does not match this vehicle.");
    }
}
