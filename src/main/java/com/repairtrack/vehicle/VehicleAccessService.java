package com.repairtrack.vehicle;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.repairtrack.security.AuthenticatedUser;
import com.repairtrack.vehicle.application.VehicleAccessDeniedException;
import com.repairtrack.vehicle.application.VehicleNotFoundException;
import com.repairtrack.vehicle.domain.OwnershipStatus;
import com.repairtrack.vehicle.infrastructure.VehicleOwnershipRepository;
import com.repairtrack.vehicle.infrastructure.VehicleRepository;

/**
 * Vehicle authorization for other modules (repair, document, sharing). Ownership is always
 * determined from {@code vehicle_ownership}, never from anything a client sends.
 */
@Service
public class VehicleAccessService {

    private final VehicleRepository vehicles;
    private final VehicleOwnershipRepository ownerships;

    public VehicleAccessService(VehicleRepository vehicles, VehicleOwnershipRepository ownerships) {
        this.vehicles = vehicles;
        this.ownerships = ownerships;
    }

    @Transactional(readOnly = true)
    public void requireExists(UUID vehicleId) {
        if (!vehicles.existsById(vehicleId)) {
            throw new VehicleNotFoundException();
        }
    }

    @Transactional(readOnly = true)
    public boolean isActiveOwner(UUID userId, UUID vehicleId) {
        return ownerships.existsByVehicleIdAndUserIdAndStatus(vehicleId, userId, OwnershipStatus.ACTIVE);
    }

    /** The caller must be the vehicle's current owner (e.g. to add owner records or share its history). */
    @Transactional(readOnly = true)
    public void requireActiveOwner(AuthenticatedUser actor, UUID vehicleId) {
        requireExists(vehicleId);
        if (!isActiveOwner(actor.id(), vehicleId)) {
            throw new VehicleAccessDeniedException("Only the current owner of this vehicle can do this.");
        }
    }
}
