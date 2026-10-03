package com.repairtrack.vehicle;

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
import com.repairtrack.vehicle.application.OwnershipChangedException;
import com.repairtrack.vehicle.application.OwnershipProofInvalidException;
import com.repairtrack.vehicle.application.VehicleAlreadyOwnedException;
import com.repairtrack.vehicle.application.VehicleNotFoundException;
import com.repairtrack.vehicle.domain.InvalidOwnershipPeriodException;
import com.repairtrack.vehicle.domain.OwnershipStatus;
import com.repairtrack.vehicle.domain.Vehicle;
import com.repairtrack.vehicle.domain.VehicleOwnership;
import com.repairtrack.vehicle.infrastructure.VehicleOwnershipRepository;
import com.repairtrack.vehicle.infrastructure.VehicleRepository;

/**
 * What the dispute module may do with ownership. No authorization here: the dispute module checks who acts
 * (claimant, current owner, system admin) before calling.
 */
@Service
public class OwnershipDisputeSupport {

    private final VehicleRepository vehicles;
    private final VehicleOwnershipRepository ownerships;
    private final ApplicationEventPublisher events;
    private final BusinessCalendar calendar;

    public OwnershipDisputeSupport(VehicleRepository vehicles, VehicleOwnershipRepository ownerships,
                                   ApplicationEventPublisher events, BusinessCalendar calendar) {
        this.vehicles = vehicles;
        this.ownerships = ownerships;
        this.events = events;
        this.calendar = calendar;
    }

    @Transactional(readOnly = true)
    public Optional<CurrentOwnership> currentOwnership(UUID vehicleId) {
        vehicles.findById(vehicleId).orElseThrow(VehicleNotFoundException::new);
        return ownerships.findByVehicleIdAndStatus(vehicleId, OwnershipStatus.ACTIVE)
                .map(o -> new CurrentOwnership(o.getId(), o.getUserId(), o.getStartDate()));
    }

    /** Same proof as a claim: the full VIN. Throws {@code OWNERSHIP_PROOF_INVALID} when it does not match. */
    @Transactional(readOnly = true)
    public void requireVinMatches(UUID vehicleId, String vin) {
        Vehicle vehicle = vehicles.findById(vehicleId).orElseThrow(VehicleNotFoundException::new);
        byte[] expected = vehicle.getVin().getBytes(StandardCharsets.US_ASCII);
        byte[] given = Vehicle.normalizeVin(vin).getBytes(StandardCharsets.US_ASCII);
        if (!MessageDigest.isEqual(expected, given)) { // constant time: the VIN acts as a secret here
            throw new OwnershipProofInvalidException();
        }
    }

    /**
     * An upheld dispute: revokes {@code contestedOwnershipId} (still in the history, marked REVOKED) and makes
     * {@code newOwnerId} the owner from {@code newOwnerSince} (default today). Fails with {@code OWNERSHIP_CHANGED}
     * when the contested ownership is no longer the active one.
     */
    @Transactional
    public void transferAfterUpheldDispute(UUID disputeId, UUID vehicleId, UUID contestedOwnershipId,
                                           UUID newOwnerId, LocalDate newOwnerSince, UUID decidedBy) {
        vehicles.findByIdForUpdate(vehicleId).orElseThrow(VehicleNotFoundException::new);
        VehicleOwnership contested = ownerships.findByVehicleIdAndStatus(vehicleId, OwnershipStatus.ACTIVE)
                .filter(o -> o.getId().equals(contestedOwnershipId))
                .orElseThrow(OwnershipChangedException::new);
        Instant now = calendar.now();
        LocalDate today = calendar.today();
        contested.revoke(now, today);
        ownerships.saveAndFlush(contested);

        LocalDate start = newOwnerSince != null ? newOwnerSince : today;
        Optional<LocalDate> previousEnd = ownerships.findLatestEndDate(vehicleId);
        if (previousEnd.isPresent() && start.isBefore(previousEnd.get())) {
            throw new InvalidOwnershipPeriodException(
                    "Ownership cannot start before the previous ownership ended (" + previousEnd.get() + ").");
        }
        VehicleOwnership ownership = VehicleOwnership.start(vehicleId, newOwnerId, start, now, today);
        try {
            ownerships.saveAndFlush(ownership);
        } catch (DataIntegrityViolationException ex) {
            throw new VehicleAlreadyOwnedException();
        }
        events.publishEvent(new VehicleEvents.VehicleOwnershipRevoked(vehicleId, contested.getUserId(),
                contested.getEndDate(), decidedBy, disputeId, now));
        events.publishEvent(new VehicleEvents.VehicleOwnershipAssigned(vehicleId, newOwnerId, start, decidedBy,
                disputeId, now));
    }
}
