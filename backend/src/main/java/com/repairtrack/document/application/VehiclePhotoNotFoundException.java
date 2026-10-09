package com.repairtrack.document.application;

import com.repairtrack.common.error.ApplicationException;
import com.repairtrack.common.error.ErrorCategory;

/** The caller has not added a photo to this vehicle (yet). */
public class VehiclePhotoNotFoundException extends ApplicationException {

    public VehiclePhotoNotFoundException() {
        super(ErrorCategory.NOT_FOUND, "VEHICLE_PHOTO_NOT_FOUND", "This vehicle has no photo.");
    }
}
