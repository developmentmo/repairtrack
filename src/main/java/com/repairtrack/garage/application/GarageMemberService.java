package com.repairtrack.garage.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.repairtrack.garage.GarageAccessService;
import com.repairtrack.garage.GarageEvents;
import com.repairtrack.garage.GarageRole;
import com.repairtrack.garage.domain.GarageUser;
import com.repairtrack.garage.domain.MembershipStatus;
import com.repairtrack.garage.infrastructure.GarageRepository;
import com.repairtrack.garage.infrastructure.GarageUserRepository;
import com.repairtrack.security.AuthenticatedUser;
import com.repairtrack.security.UserDirectory;
import com.repairtrack.security.UserSummary;

/**
 * Garage membership management. Every mutation first locks the garage row, so concurrent
 * requests cannot, for example, both remove "the other" admin and leave the garage without one.
 */
@Service
public class GarageMemberService {

    private final GarageRepository garages;
    private final GarageUserRepository memberships;
    private final GarageAccessService access;
    private final UserDirectory userDirectory;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public GarageMemberService(GarageRepository garages, GarageUserRepository memberships,
                               GarageAccessService access, UserDirectory userDirectory,
                               ApplicationEventPublisher events, Clock clock) {
        this.garages = garages;
        this.memberships = memberships;
        this.access = access;
        this.userDirectory = userDirectory;
        this.events = events;
        this.clock = clock;
    }

    /**
     * A garage admin adds an existing RepairTrack user by email. The role is chosen by the
     * garage admin (who is authorized for this garage), never by the person being added.
     */
    @Transactional
    public GarageMemberView addMember(AuthenticatedUser actor, UUID garageId, String email, GarageRole role) {
        garages.findByIdForUpdate(garageId).orElseThrow(GarageNotFoundException::new);
        access.requireGarageAdmin(actor, garageId);

        UserSummary user = userDirectory.findActiveByEmail(email).orElseThrow(UserNotRegisteredException::new);
        if (memberships.existsByGarageIdAndUserIdAndStatus(garageId, user.id(), MembershipStatus.ACTIVE)) {
            throw new AlreadyGarageMemberException();
        }
        Instant now = Instant.now(clock);
        GarageUser membership = GarageUser.add(garageId, user.id(), role, actor.id(), now);
        try {
            memberships.saveAndFlush(membership);
        } catch (DataIntegrityViolationException ex) {
            throw new AlreadyGarageMemberException(); // partial unique index on active memberships
        }
        events.publishEvent(new GarageEvents.GarageMemberAdded(garageId, user.id(), role, actor.id(), now));
        return view(membership, user);
    }

    @Transactional(readOnly = true)
    public List<GarageMemberView> listMembers(AuthenticatedUser actor, UUID garageId) {
        access.requireMemberOrSystemAdmin(actor, garageId);
        List<GarageUser> active = memberships.findByGarageIdAndStatus(garageId, MembershipStatus.ACTIVE);
        Map<UUID, UserSummary> users = userDirectory.findByIds(active.stream().map(GarageUser::getUserId).toList());
        return active.stream()
                .filter(membership -> users.containsKey(membership.getUserId()))
                .map(membership -> view(membership, users.get(membership.getUserId())))
                .sorted(Comparator.comparing(GarageMemberView::role)
                        .thenComparing(GarageMemberView::lastName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /**
     * Ends a membership. Allowed for garage admins, and for members removing themselves (leaving).
     * The last garage admin can never be removed.
     */
    @Transactional
    public void removeMember(AuthenticatedUser actor, UUID garageId, UUID userId) {
        garages.findByIdForUpdate(garageId).orElseThrow(GarageNotFoundException::new);
        if (!actor.id().equals(userId)) {
            access.requireGarageAdmin(actor, garageId);
        }
        GarageUser membership = memberships
                .findByGarageIdAndUserIdAndStatus(garageId, userId, MembershipStatus.ACTIVE)
                .orElseThrow(GarageMemberNotFoundException::new);
        if (membership.isAdmin()
                && memberships.countByGarageIdAndRoleAndStatus(garageId, GarageRole.GARAGE_ADMIN,
                MembershipStatus.ACTIVE) <= 1) {
            throw new LastGarageAdminException();
        }
        Instant now = Instant.now(clock);
        membership.end(actor.id(), now);
        events.publishEvent(new GarageEvents.GarageMemberRemoved(garageId, userId, actor.id(), now));
    }

    private static GarageMemberView view(GarageUser membership, UserSummary user) {
        return new GarageMemberView(user.id(), user.email(), user.firstName(), user.lastName(),
                membership.getRole(), membership.getCreatedAt());
    }
}
