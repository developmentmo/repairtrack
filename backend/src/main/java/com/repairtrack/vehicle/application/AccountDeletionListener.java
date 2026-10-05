package com.repairtrack.vehicle.application;

import java.time.Instant;
import java.time.LocalDate;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.repairtrack.common.time.BusinessCalendar;
import com.repairtrack.security.UserAccountEvents;
import com.repairtrack.vehicle.VehicleEvents;
import com.repairtrack.vehicle.domain.OwnershipStatus;
import com.repairtrack.vehicle.domain.VehicleOwnership;
import com.repairtrack.vehicle.infrastructure.VehicleOwnershipRepository;

/**
 * A deleted account owns nothing any more: its active ownerships end today, exactly as if the owner reported the
 * vehicles as sold. The history stays with the vehicles, which can be claimed by their next owner; the user's share
 * links stop working because they are tied to the ownership. Runs in the deleting transaction.
 */
@Component
class AccountDeletionListener {

    private final VehicleOwnershipRepository ownerships;
    private final ApplicationEventPublisher events;
    private final BusinessCalendar calendar;

    AccountDeletionListener(VehicleOwnershipRepository ownerships, ApplicationEventPublisher events,
                            BusinessCalendar calendar) {
        this.ownerships = ownerships;
        this.events = events;
        this.calendar = calendar;
    }

    @EventListener
    void on(UserAccountEvents.AccountDeleted event) {
        Instant now = calendar.now();
        LocalDate today = calendar.today();
        for (VehicleOwnership ownership : ownerships.findByUserIdAndStatus(event.userId(), OwnershipStatus.ACTIVE)) {
            ownership.end(today, now, today);
            ownerships.save(ownership);
            events.publishEvent(new VehicleEvents.VehicleOwnershipEnded(ownership.getVehicleId(), event.userId(),
                    today, now));
        }
    }
}
