package com.repairtrack.dispute.application;

import com.repairtrack.common.error.ApplicationException;
import com.repairtrack.common.error.ErrorCategory;

/** The dispute module's application errors (stable codes for the clients). */
public final class DisputeExceptions {

    private DisputeExceptions() {
    }

    public static class DisputeNotFoundException extends ApplicationException {

        public DisputeNotFoundException() {
            super(ErrorCategory.NOT_FOUND, "DISPUTE_NOT_FOUND", "Dispute not found.");
        }
    }

    public static class DisputeAlreadyOpenException extends ApplicationException {

        public DisputeAlreadyOpenException() {
            super(ErrorCategory.CONFLICT, "DISPUTE_ALREADY_OPEN",
                    "You already have an open dispute about this vehicle.");
        }
    }

    public static class TooManyOpenDisputesException extends ApplicationException {

        public TooManyOpenDisputesException() {
            super(ErrorCategory.BUSINESS_RULE_VIOLATION, "TOO_MANY_OPEN_DISPUTES",
                    "You have too many open disputes. Wait until they are decided.");
        }
    }

    /** Nothing to dispute: claim the vehicle instead. */
    public static class VehicleNotOwnedException extends ApplicationException {

        public VehicleNotOwnedException() {
            super(ErrorCategory.BUSINESS_RULE_VIOLATION, "VEHICLE_NOT_OWNED",
                    "This vehicle has no owner. Claim it instead.");
        }
    }

    public static class AlreadyOwnerException extends ApplicationException {

        public AlreadyOwnerException() {
            super(ErrorCategory.CONFLICT, "ALREADY_VEHICLE_OWNER", "You are already the owner of this vehicle.");
        }
    }

    public static class DisputeAccessDeniedException extends ApplicationException {

        public DisputeAccessDeniedException(String message) {
            super(ErrorCategory.FORBIDDEN, "DISPUTE_ACCESS_DENIED", message);
        }
    }

    public static class SystemAdminRequiredException extends ApplicationException {

        public SystemAdminRequiredException() {
            super(ErrorCategory.FORBIDDEN, "SYSTEM_ADMIN_REQUIRED", "Only system administrators may do this.");
        }
    }

    public static class TooMuchEvidenceException extends ApplicationException {

        public TooMuchEvidenceException(int max) {
            super(ErrorCategory.BUSINESS_RULE_VIOLATION, "TOO_MUCH_EVIDENCE",
                    "At most " + max + " files per party.");
        }
    }
}
