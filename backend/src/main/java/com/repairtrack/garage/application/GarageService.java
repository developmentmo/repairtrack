package com.repairtrack.garage.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.repairtrack.garage.GarageAccessService;
import com.repairtrack.garage.GarageEvents;
import com.repairtrack.garage.GarageRole;
import com.repairtrack.garage.GarageVerificationStatus;
import com.repairtrack.garage.domain.Garage;
import com.repairtrack.garage.domain.GarageUser;
import com.repairtrack.garage.domain.MembershipStatus;
import com.repairtrack.garage.infrastructure.GarageRepository;
import com.repairtrack.garage.infrastructure.GarageUserRepository;
import com.repairtrack.security.AuthenticatedUser;
import com.repairtrack.security.Role;

/** Garage registration, profile, and verification lifecycle. */
@Service
public class GarageService {

    private final GarageRepository garages;
    private final GarageUserRepository memberships;
    private final GarageAccessService access;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public GarageService(GarageRepository garages, GarageUserRepository memberships, GarageAccessService access,
                         ApplicationEventPublisher events, Clock clock) {
        this.garages = garages;
        this.memberships = memberships;
        this.access = access;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Any authenticated user can register a garage and becomes its first GARAGE_ADMIN.
     * The garage starts PENDING and can record work (as unverified source) until reviewed.
     */
    @Transactional
    public GarageView register(AuthenticatedUser actor, RegisterGarageCommand command) {
        Instant now = Instant.now(clock);
        Garage garage = Garage.register(command.name(), command.kvkNumber(), command.address(),
                command.postalCode(), command.city(), command.phone(), command.email(), actor.id(), now);
        garages.save(garage);
        GarageUser founder = GarageUser.add(garage.getId(), actor.id(), GarageRole.GARAGE_ADMIN, actor.id(), now);
        memberships.save(founder);

        events.publishEvent(new GarageEvents.GarageRegistered(garage.getId(), actor.id(), now));
        events.publishEvent(new GarageEvents.GarageMemberAdded(garage.getId(), actor.id(),
                GarageRole.GARAGE_ADMIN, actor.id(), now));
        return GarageView.of(garage);
    }

    /** Garage profiles are business information, visible to every authenticated user. */
    @Transactional(readOnly = true)
    public GarageView get(UUID garageId) {
        return garages.findById(garageId).map(GarageView::of).orElseThrow(GarageNotFoundException::new);
    }

    /** The garages the caller currently works at. */
    @Transactional(readOnly = true)
    public List<GarageMembershipView> myGarages(AuthenticatedUser actor) {
        List<GarageUser> active = memberships.findByUserIdAndStatus(actor.id(), MembershipStatus.ACTIVE);
        Map<UUID, Garage> garagesById = garages.findAllById(active.stream().map(GarageUser::getGarageId).toList())
                .stream()
                .collect(Collectors.toMap(Garage::getId, Function.identity()));
        return active.stream()
                .filter(membership -> garagesById.containsKey(membership.getGarageId()))
                .map(membership -> {
                    Garage garage = garagesById.get(membership.getGarageId());
                    return new GarageMembershipView(garage.getId(), garage.getName(), garage.getCity(),
                            garage.getVerificationStatus(), membership.getRole(), membership.getCreatedAt());
                })
                .sorted(Comparator.comparing(GarageMembershipView::garageName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /** A garage admin re-applies for verification after being set to UNVERIFIED. */
    @Transactional
    public GarageView requestVerification(AuthenticatedUser actor, UUID garageId) {
        Garage garage = garages.findByIdForUpdate(garageId).orElseThrow(GarageNotFoundException::new);
        access.requireGarageAdmin(actor, garageId);
        Instant now = Instant.now(clock);
        GarageVerificationStatus previous = garage.requestVerification(actor.id(), now);
        events.publishEvent(new GarageEvents.GarageVerificationStatusChanged(garageId, previous,
                garage.getVerificationStatus(), actor.id(), null, now));
        return GarageView.of(garage);
    }

    /** System admin: garages with the given verification status (e.g. the PENDING queue), oldest first. */
    @Transactional(readOnly = true)
    public List<GarageView> byVerificationStatus(AuthenticatedUser actor, GarageVerificationStatus status) {
        if (!actor.hasRole(Role.SYSTEM_ADMIN)) {
            throw new SystemAdminRequiredException();
        }
        return garages.findByVerificationStatusOrderByCreatedAtAsc(status).stream().map(GarageView::of).toList();
    }

    /** Verification decision. Only system admins; checked here, not via URL rules. */
    @Transactional
    public GarageView decideVerification(AuthenticatedUser actor, UUID garageId,
                                         GarageVerificationStatus target, String note) {
        if (!actor.hasRole(Role.SYSTEM_ADMIN)) {
            throw new SystemAdminRequiredException();
        }
        Garage garage = garages.findByIdForUpdate(garageId).orElseThrow(GarageNotFoundException::new);
        Instant now = Instant.now(clock);
        GarageVerificationStatus previous = garage.decideVerification(target, actor.id(), note, now);
        events.publishEvent(new GarageEvents.GarageVerificationStatusChanged(garageId, previous, target,
                actor.id(), garage.getVerificationNote(), now));
        return GarageView.of(garage);
    }
}
