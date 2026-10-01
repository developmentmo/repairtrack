package com.repairtrack.garage;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.repairtrack.garage.application.GarageAccessDeniedException;
import com.repairtrack.garage.application.GarageNotFoundException;
import com.repairtrack.garage.application.GarageSuspendedException;
import com.repairtrack.garage.domain.Garage;
import com.repairtrack.garage.domain.GarageUser;
import com.repairtrack.garage.domain.MembershipStatus;
import com.repairtrack.garage.infrastructure.GarageRepository;
import com.repairtrack.garage.infrastructure.GarageUserRepository;
import com.repairtrack.security.AuthenticatedUser;
import com.repairtrack.security.Role;

/**
 * Explicit, data-based authorization for everything a user does on behalf of a garage.
 * Part of the garage module's public API: other modules call {@link #validateCanRecordWork}
 * before doing anything on behalf of a garage (registering a customer's vehicle, recording a repair).
 * <p>
 * Checks are based on the caller's ACTIVE membership of the specific garage, never on a
 * global role. A SYSTEM_ADMIN is not implicitly a garage member.
 */
@Service
public class GarageAccessService {

    private final GarageRepository garages;
    private final GarageUserRepository memberships;

    public GarageAccessService(GarageRepository garages, GarageUserRepository memberships) {
        this.garages = garages;
        this.memberships = memberships;
    }

    /**
     * Current user &rarr; active member of the garage (admin or mechanic) &rarr; garage not suspended.
     * Whether the garage may work on a particular vehicle is checked by the vehicle/repair modules.
     *
     * @return a permit describing the membership and the garage's verification status
     * @throws GarageNotFoundException      if the garage does not exist
     * @throws GarageAccessDeniedException  if the caller is not an active member
     * @throws GarageSuspendedException     if the garage is suspended
     */
    @Transactional(readOnly = true)
    public GarageWorkPermit validateCanRecordWork(AuthenticatedUser actor, UUID garageId) {
        Garage garage = garages.findById(garageId).orElseThrow(GarageNotFoundException::new);
        GarageUser membership = activeMembership(actor.id(), garageId)
                .orElseThrow(() -> new GarageAccessDeniedException("You are not a member of this garage."));
        if (garage.isSuspended()) {
            throw new GarageSuspendedException();
        }
        return new GarageWorkPermit(garageId, actor.id(), membership.getRole(), garage.getVerificationStatus());
    }

    /** Plain membership check, e.g. for "may this garage's staff edit a vehicle they registered". */
    @Transactional(readOnly = true)
    public boolean isActiveMember(UUID userId, UUID garageId) {
        return activeMembership(userId, garageId).isPresent();
    }

    /** IDs of all garages the user is currently an active member of. */
    @Transactional(readOnly = true)
    public Set<UUID> activeGarageIds(UUID userId) {
        return memberships.findByUserIdAndStatus(userId, MembershipStatus.ACTIVE).stream()
                .map(GarageUser::getGarageId)
                .collect(Collectors.toUnmodifiableSet());
    }

    /** Caller must be an active GARAGE_ADMIN of the garage. */
    @Transactional(readOnly = true)
    public void requireGarageAdmin(AuthenticatedUser actor, UUID garageId) {
        requireExists(garageId);
        boolean admin = activeMembership(actor.id(), garageId).map(GarageUser::isAdmin).orElse(false);
        if (!admin) {
            throw new GarageAccessDeniedException("Only a garage admin of this garage can perform this action.");
        }
    }

    /** Caller must be an active member of the garage, or a system admin. */
    @Transactional(readOnly = true)
    public void requireMemberOrSystemAdmin(AuthenticatedUser actor, UUID garageId) {
        requireExists(garageId);
        if (actor.hasRole(Role.SYSTEM_ADMIN)) {
            return;
        }
        if (activeMembership(actor.id(), garageId).isEmpty()) {
            throw new GarageAccessDeniedException("You are not a member of this garage.");
        }
    }

    private void requireExists(UUID garageId) {
        if (!garages.existsById(garageId)) {
            throw new GarageNotFoundException();
        }
    }

    private Optional<GarageUser> activeMembership(UUID userId, UUID garageId) {
        return memberships.findByGarageIdAndUserIdAndStatus(garageId, userId, MembershipStatus.ACTIVE);
    }
}
