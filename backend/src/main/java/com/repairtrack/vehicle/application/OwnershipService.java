package com.repairtrack.vehicle.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.repairtrack.common.time.BusinessCalendar;
import com.repairtrack.security.AuthenticatedUser;
import com.repairtrack.vehicle.VehicleEvents;
import com.repairtrack.vehicle.domain.InvalidOwnershipPeriodException;
import com.repairtrack.vehicle.domain.OwnershipStatus;
import com.repairtrack.vehicle.domain.Vehicle;
import com.repairtrack.vehicle.domain.VehicleOwnership;
import com.repairtrack.vehicle.infrastructure.VehicleOwnershipRepository;
import com.repairtrack.vehicle.infrastructure.VehicleRepository;

/**
 * Claiming and ending vehicle ownership. Both lock the vehicle row, so two people cannot claim
 * the same vehicle at the same moment (the partial unique index is the last line of defence).
 */
@Service
public class OwnershipService {

    private final VehicleRepository vehicles;
    private final VehicleOwnershipRepository ownerships;
    private final ApplicationEventPublisher events;
    private final BusinessCalendar calendar;

    public OwnershipService(VehicleRepository vehicles, VehicleOwnershipRepository ownerships,
                            ApplicationEventPublisher events, BusinessCalendar calendar) {
        this.vehicles = vehicles;
        this.ownerships = ownerships;
        this.events = events;
        this.calendar = calendar;
    }

    /**
     * Claim ownership of a vehicle that currently has no owner.
     * <p>
     * Proof in V1: the claimant must supply the full VIN (from the registration documents), which
     * is never shown to non-owners by the API. This is deliberately modest proof; stronger
     * verification (documents, RDW) can raise it later without changing this contract.
     */
    @Transactional
    public OwnershipView claim(AuthenticatedUser actor, UUID vehicleId, String vin, LocalDate ownedSince) {
        Vehicle vehicle = vehicles.findByIdForUpdate(vehicleId).orElseThrow(VehicleNotFoundException::new);
        if (!sameVin(vehicle.getVin(), Vehicle.normalizeVin(vin))) {
            throw new OwnershipProofInvalidException();
        }
        Optional<VehicleOwnership> current = ownerships.findByVehicleIdAndStatus(vehicleId, OwnershipStatus.ACTIVE);
        if (current.isPresent()) {
            if (current.get().getUserId().equals(actor.id())) {
                throw new AlreadyVehicleOwnerException();
            }
            throw new VehicleAlreadyOwnedException();
        }

        Instant now = calendar.now();
        LocalDate today = calendar.today();
        LocalDate start = ownedSince != null ? ownedSince : today;
        Optional<LocalDate> previousEnd = ownerships.findLatestEndDate(vehicleId);
        if (previousEnd.isPresent() && start.isBefore(previousEnd.get())) {
            throw new InvalidOwnershipPeriodException(
                    "Ownership cannot start before the previous ownership ended (" + previousEnd.get() + ").");
        }
        VehicleOwnership ownership = VehicleOwnership.start(vehicleId, actor.id(), start, now, today);
        try {
            ownerships.saveAndFlush(ownership);
        } catch (DataIntegrityViolationException ex) {
            throw new VehicleAlreadyOwnedException(); // partial unique index: one active owner
        }
        events.publishEvent(new VehicleEvents.VehicleOwnershipStarted(vehicleId, actor.id(), start, true, now));
        return OwnershipView.of(ownership);
    }

    /** The current owner ends their ownership (sold, exported, scrapped). Default end date: today. */
    @Transactional
    public OwnershipView endOwnership(AuthenticatedUser actor, UUID vehicleId, LocalDate endDate) {
        vehicles.findByIdForUpdate(vehicleId).orElseThrow(VehicleNotFoundException::new);
        VehicleOwnership ownership = ownerships.findByVehicleIdAndStatus(vehicleId, OwnershipStatus.ACTIVE)
                .filter(o -> o.getUserId().equals(actor.id()))
                .orElseThrow(() -> new VehicleAccessDeniedException("Only the current owner can end the ownership."));
        Instant now = calendar.now();
        LocalDate today = calendar.today();
        LocalDate end = endDate != null ? endDate : today;
        ownership.end(end, now, today);
        events.publishEvent(new VehicleEvents.VehicleOwnershipEnded(vehicleId, actor.id(), end, now));
        return OwnershipView.of(ownership);
    }

    /** Constant-time comparison: the VIN acts as a secret here. */
    private static boolean sameVin(String expected, String given) {
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                given.getBytes(StandardCharsets.US_ASCII));
    }
}
