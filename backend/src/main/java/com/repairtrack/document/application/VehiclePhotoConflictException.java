package com.repairtrack.document.application;

import com.repairtrack.common.error.ApplicationException;
import com.repairtrack.common.error.ErrorCategory;

/** Another upload for the same vehicle finished first; the client may simply try again. */
public class VehiclePhotoConflictException extends ApplicationException {

    public VehiclePhotoConflictException() {
        super(ErrorCategory.CONFLICT, "VEHICLE_PHOTO_CONFLICT",
                "Another photo was uploaded for this vehicle at the same time. Please try again.");
    }
}
