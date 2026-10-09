package com.repairtrack.document.infrastructure;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.repairtrack.document.domain.VehiclePhoto;

/** Never deleted: replaced photos stay as {@code REPLACED}. */
public interface VehiclePhotoRepository extends JpaRepository<VehiclePhoto, UUID> {

    Optional<VehiclePhoto> findByVehicleIdAndUploadedByAndStatus(UUID vehicleId, UUID uploadedBy,
                                                                 VehiclePhoto.Status status);
}
