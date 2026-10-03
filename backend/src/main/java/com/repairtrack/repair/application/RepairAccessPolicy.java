package com.repairtrack.repair.application;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import org.springframework.stereotype.Component;

import com.repairtrack.common.error.ApplicationException;
import com.repairtrack.garage.GarageAccessService;
import com.repairtrack.repair.application.Views.RepairPermissions;
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
 * same as correct, plus SYSTEM_ADMIN (moderation), plus the current owner for owner records entered by a user
 * whose ownership was revoked after an upheld dispute (they can never change those records otherwise).
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
        if (!event.isGarageRecord() && vehicleAccess.isActiveOwner(actor.id(), event.getVehicleId())
                && vehicleDirectory.revokedOwners(event.getVehicleId()).contains(event.getCreatedBy())) {
            return;
        }
        requireCanModify(actor, event);
    }

    /**
     * What {@code actor} may do with each record: the non-throwing form of {@link #requireCanModify} and
     * {@link #requireCanVoid}. Garage and ownership lookups are cached per returned function, so a whole history
     * costs a few queries instead of a few per record. Voided records can no longer be changed.
     */
    public Function<RepairEvent, RepairPermissions> permissionsFor(AuthenticatedUser actor) {
        boolean systemAdmin = actor.hasRole(Role.SYSTEM_ADMIN);
        Map<UUID, Boolean> garageMayWork = new HashMap<>();
        Map<UUID, Boolean> activeOwner = new HashMap<>();
        Map<UUID, Set<UUID>> revokedOwners = new HashMap<>();
        return event -> {
            if (event.isVoided()) {
                return RepairPermissions.NONE;
            }
            if (event.isGarageRecord()) {
                boolean canModify = garageMayWork.computeIfAbsent(event.getGarageId(),
                        garageId -> mayRecordWork(actor, garageId));
                return new RepairPermissions(canModify, canModify || systemAdmin);
            }
            boolean isActiveOwner = activeOwner.computeIfAbsent(event.getVehicleId(),
                    vehicleId -> vehicleAccess.isActiveOwner(actor.id(), vehicleId));
            boolean canModify = isActiveOwner && event.getCreatedBy().equals(actor.id());
            boolean byRevokedOwner = isActiveOwner && revokedOwners
                    .computeIfAbsent(event.getVehicleId(), vehicleDirectory::revokedOwners)
                    .contains(event.getCreatedBy());
            return new RepairPermissions(canModify, canModify || systemAdmin || byRevokedOwner);
        };
    }

    private boolean mayRecordWork(AuthenticatedUser actor, UUID garageId) {
        if (!garageAccess.isActiveMember(actor.id(), garageId)) {
            return false;
        }
        try {
            garageAccess.validateCanRecordWork(actor, garageId);
            return true;
        } catch (ApplicationException e) {
            return false; // e.g. garage suspended
        }
    }
}
