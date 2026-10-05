package com.repairtrack.garage.application;

import java.time.Clock;
import java.time.Instant;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.repairtrack.garage.GarageEvents;
import com.repairtrack.garage.GarageRole;
import com.repairtrack.garage.domain.GarageUser;
import com.repairtrack.garage.domain.MembershipStatus;
import com.repairtrack.garage.infrastructure.GarageRepository;
import com.repairtrack.garage.infrastructure.GarageUserRepository;
import com.repairtrack.security.UserAccountEvents;

/**
 * A deleted account leaves its garages. Refused (the whole deletion) when the user is the only garage admin of a
 * garage that still has other members: someone else must become admin first, or the garage would be unmanageable.
 * A garage where the user was the only member simply has no members any more. Work recorded by the user stays.
 */
@Component
class GarageAccountDeletionListener {

    private final GarageRepository garages;
    private final GarageUserRepository memberships;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    GarageAccountDeletionListener(GarageRepository garages, GarageUserRepository memberships,
                            ApplicationEventPublisher events, Clock clock) {
        this.garages = garages;
        this.memberships = memberships;
        this.events = events;
        this.clock = clock;
    }

    @EventListener
    void on(UserAccountEvents.AccountDeleted event) {
        Instant now = Instant.now(clock);
        for (GarageUser membership : memberships.findByUserIdAndStatus(event.userId(), MembershipStatus.ACTIVE)) {
            garages.findByIdForUpdate(membership.getGarageId());
            if (membership.isAdmin()) {
                long admins = memberships.countByGarageIdAndRoleAndStatus(membership.getGarageId(),
                        GarageRole.GARAGE_ADMIN, MembershipStatus.ACTIVE);
                long members = memberships.findByGarageIdAndStatus(membership.getGarageId(), MembershipStatus.ACTIVE)
                        .size();
                if (admins <= 1 && members > 1) {
                    throw new LastGarageAdminException();
                }
            }
            membership.end(event.userId(), now);
            memberships.save(membership);
            events.publishEvent(new GarageEvents.GarageMemberRemoved(membership.getGarageId(), event.userId(),
                    event.userId(), now));
        }
    }
}
