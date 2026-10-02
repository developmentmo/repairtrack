package com.repairtrack.vehicle.application;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.repairtrack.garage.GarageAccessService;
import com.repairtrack.security.AuthenticatedUser;
import com.repairtrack.security.Role;
import com.repairtrack.vehicle.domain.Vehicle;

/**
 * Who may see or change what on a vehicle. Kept in one place so the rules are reviewable together.
 *
 * <table>
 *   <tr><th></th><th>see VIN</th><th>edit details</th></tr>
 *   <tr><td>active owner</td><td>yes</td><td>yes</td></tr>
 *   <tr><td>SYSTEM_ADMIN</td><td>yes</td><td>yes</td></tr>
 *   <tr><td>member of the garage that registered it</td><td>yes</td><td>only while the vehicle has no owner</td></tr>
 *   <tr><td>anyone else (authenticated)</td><td>no</td><td>no</td></tr>
 * </table>
 * The VIN is hidden from everyone else because it is used as proof when claiming a vehicle.
 */
@Component
class VehiclePermissions {

    private final GarageAccessService garageAccess;

    VehiclePermissions(GarageAccessService garageAccess) {
        this.garageAccess = garageAccess;
    }

    boolean canSeeVin(AuthenticatedUser actor, Vehicle vehicle, UUID activeOwnerId) {
        return actor.id().equals(activeOwnerId)
                || actor.hasRole(Role.SYSTEM_ADMIN)
                || isMemberOfRegisteringGarage(actor, vehicle);
    }

    boolean canEditDetails(AuthenticatedUser actor, Vehicle vehicle, UUID activeOwnerId) {
        if (actor.id().equals(activeOwnerId) || actor.hasRole(Role.SYSTEM_ADMIN)) {
            return true;
        }
        return activeOwnerId == null && isMemberOfRegisteringGarage(actor, vehicle);
    }

    private boolean isMemberOfRegisteringGarage(AuthenticatedUser actor, Vehicle vehicle) {
        return vehicle.getRegisteredByGarageId() != null
                && garageAccess.isActiveMember(actor.id(), vehicle.getRegisteredByGarageId());
    }
}
