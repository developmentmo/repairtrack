package com.repairtrack.repair.application;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.repairtrack.garage.GarageAccessService;
import com.repairtrack.repair.domain.RepairEvent;
import com.repairtrack.repair.infrastructure.RepairEventRepository;
import com.repairtrack.security.AuthenticatedUser;
import com.repairtrack.security.Role;
import com.repairtrack.vehicle.VehicleAccessService;
import com.repairtrack.vehicle.VehicleDirectory;

/**
 * Who may see a vehicle's history and who may change which record. All rules in one place.
 *
 * <h2>View history</h2>
 * current owner &middot; SYSTEM_ADMIN &middot; members of a garage that registered the vehicle or has
 * recorded work on it. Previous owners and other users cannot (public sharing is Phase 7).
 *
 * <h2>Create</h2>
 * owner record: current owner &middot; garage record: {@code GarageAccessService.validateCanRecordWork}.
 * A garage may record work on any registered vehicle; it has the car in its workshop.
 *
 * <h2>Correct / add parts</h2>
 * garage record: active member of THAT garage (garage not suspended) &middot;
 * owner record: the owner who created it, while still the current owner.
 * An owner can never change a garage record, and a garage never an owner record.
 *
 * <h2>Void</h2>
 * same as correct, plus SYSTEM_ADMIN (moderation).
 */
@Component
public class RepairAccessPolicy {

    private final VehicleAccessService vehicleAccess;
    private final VehicleDirectory vehicleDirectory;
    private final GarageAccessService garageAccess;
    private final RepairEventRepository repairs;

    public RepairAccessPolicy(VehicleAccessService vehicleAccess, VehicleDirectory vehicleDirectory,
                       GarageAccessService garageAccess, RepairEventRepository repairs) {
        this.vehicleAccess = vehicleAccess;
        this.vehicleDirectory = vehicleDirectory;
        this.garageAccess = garageAccess;
        this.repairs = repairs;
    }

    public void requireCanViewHistory(AuthenticatedUser actor, UUID vehicleId) {
        vehicleAccess.requireExists(vehicleId);
        if (!canViewHistory(actor, vehicleId)) {
            throw new RepairAccessDeniedException("You are not allowed to see this vehicle's history.");
        }
    }

    public boolean canViewHistory(AuthenticatedUser actor, UUID vehicleId) {
        if (actor.hasRole(Role.SYSTEM_ADMIN) || vehicleAccess.isActiveOwner(actor.id(), vehicleId)) {
            return true;
        }
        Set<UUID> myGarages = garageAccess.activeGarageIds(actor.id());
        if (myGarages.isEmpty()) {
            return false;
        }
        Optional<UUID> registeringGarage = vehicleDirectory.registeringGarageId(vehicleId);
        if (registeringGarage.isPresent() && myGarages.contains(registeringGarage.get())) {
            return true;
        }
        return repairs.existsByVehicleIdAndGarageIdIn(vehicleId, myGarages);
    }

    /**
     * @return the garage on whose behalf the change is made, or {@code null} for the owner
     */
    public UUID requireCanModify(AuthenticatedUser actor, RepairEvent event) {
        if (event.isGarageRecord()) {
            if (!garageAccess.isActiveMember(actor.id(), event.getGarageId())) {
                throw new RepairAccessDeniedException("Only the garage that recorded this work can change it.");
            }
            garageAccess.validateCanRecordWork(actor, event.getGarageId()); // e.g. garage suspended
            return event.getGarageId();
        }
        if (event.getCreatedBy().equals(actor.id()) && vehicleAccess.isActiveOwner(actor.id(), event.getVehicleId())) {
            return null;
        }
        throw new RepairAccessDeniedException("Only the owner who created this record can change it.");
    }

    public void requireCanVoid(AuthenticatedUser actor, RepairEvent event) {
        if (actor.hasRole(Role.SYSTEM_ADMIN)) {
            return;
        }
        requireCanModify(actor, event);
    }
}
