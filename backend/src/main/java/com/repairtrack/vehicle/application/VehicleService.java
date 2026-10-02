package com.repairtrack.vehicle.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.repairtrack.common.time.BusinessCalendar;
import com.repairtrack.garage.GarageAccessService;
import com.repairtrack.security.AuthenticatedUser;
import com.repairtrack.vehicle.VehicleEvents;
import com.repairtrack.vehicle.VehicleFieldChange;
import com.repairtrack.vehicle.domain.InvalidVehicleDataException;
import com.repairtrack.vehicle.domain.OwnershipStatus;
import com.repairtrack.vehicle.domain.Vehicle;
import com.repairtrack.vehicle.domain.VehicleDetails;
import com.repairtrack.vehicle.domain.VehicleOwnership;
import com.repairtrack.vehicle.infrastructure.VehicleOwnershipRepository;
import com.repairtrack.vehicle.infrastructure.VehicleRepository;

/** Registering, finding and editing vehicles. Ownership changes are in {@link OwnershipService}. */
@Service
public class VehicleService {

    private final VehicleRepository vehicles;
    private final VehicleOwnershipRepository ownerships;
    private final VehiclePermissions permissions;
    private final GarageAccessService garageAccess;
    private final ApplicationEventPublisher events;
    private final BusinessCalendar calendar;

    public VehicleService(VehicleRepository vehicles, VehicleOwnershipRepository ownerships,
                          VehiclePermissions permissions, GarageAccessService garageAccess,
                          ApplicationEventPublisher events, BusinessCalendar calendar) {
        this.vehicles = vehicles;
        this.ownerships = ownerships;
        this.permissions = permissions;
        this.garageAccess = garageAccess;
        this.events = events;
        this.calendar = calendar;
    }

    /**
     * Registers a vehicle that is not yet known (by VIN).
     * <ul>
     *   <li>without garage: the caller becomes the owner;</li>
     *   <li>with garage: the caller must be allowed to act for that garage; the vehicle has no owner
     *       until the real owner claims it.</li>
     * </ul>
     */
    @Transactional
    public VehicleView register(AuthenticatedUser actor, RegisterVehicleCommand command) {
        String vin = Vehicle.normalizeVin(command.vin());
        UUID garageId = command.garageId();
        if (garageId != null) {
            garageAccess.validateCanRecordWork(actor, garageId);
            if (command.ownedSince() != null) {
                throw new InvalidVehicleDataException("ownedSince cannot be used when registering for a garage.");
            }
        }
        if (vehicles.existsByVin(vin)) {
            throw new VehicleAlreadyRegisteredException();
        }

        Instant now = calendar.now();
        LocalDate today = calendar.today();
        Vehicle vehicle = Vehicle.register(vin, command.details(), actor.id(), garageId, now, today);
        try {
            vehicles.saveAndFlush(vehicle);
        } catch (DataIntegrityViolationException ex) {
            throw new VehicleAlreadyRegisteredException(); // concurrent registration of the same VIN
        }
        events.publishEvent(new VehicleEvents.VehicleRegistered(vehicle.getId(), actor.id(), garageId, now));

        UUID ownerId = null;
        if (garageId == null) {
            LocalDate start = command.ownedSince() != null ? command.ownedSince() : today;
            VehicleOwnership ownership = VehicleOwnership.start(vehicle.getId(), actor.id(), start, now, today);
            ownerships.save(ownership);
            ownerId = actor.id();
            events.publishEvent(new VehicleEvents.VehicleOwnershipStarted(vehicle.getId(), actor.id(), start,
                    false, now));
        }
        return view(actor, vehicle, ownerId);
    }

    @Transactional(readOnly = true)
    public VehicleView get(AuthenticatedUser actor, UUID vehicleId) {
        Vehicle vehicle = vehicles.findById(vehicleId).orElseThrow(VehicleNotFoundException::new);
        return view(actor, vehicle, activeOwnerId(vehicleId));
    }

    /** Vehicles the caller currently owns. */
    @Transactional(readOnly = true)
    public List<VehicleView> myVehicles(AuthenticatedUser actor) {
        List<UUID> ids = ownerships.findByUserIdAndStatus(actor.id(), OwnershipStatus.ACTIVE).stream()
                .map(VehicleOwnership::getVehicleId)
                .toList();
        return vehicles.findAllById(ids).stream()
                .map(vehicle -> view(actor, vehicle, actor.id()))
                .sorted(Comparator.comparing(VehicleView::make, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(VehicleView::model, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /**
     * Exact lookup by VIN or license plate, available to every authenticated user (garages
     * identifying a customer's car, owners finding a car to claim). Results never contain the VIN.
     */
    @Transactional(readOnly = true)
    public List<VehicleSearchResult> search(String vin, String licensePlate) {
        boolean byVin = vin != null && !vin.isBlank();
        boolean byPlate = licensePlate != null && !licensePlate.isBlank();
        if (byVin == byPlate) {
            throw new InvalidVehicleSearchException();
        }
        List<Vehicle> found = byVin
                ? vehicles.findByVin(Vehicle.normalizeVin(vin)).stream().toList()
                : vehicles.findByLicensePlate(Vehicle.normalizeLicensePlate(licensePlate));
        return found.stream()
                .map(v -> new VehicleSearchResult(v.getId(), v.getLicensePlate(), v.getMake(), v.getModel(),
                        v.getModelYear()))
                .toList();
    }

    @Transactional
    public VehicleView updateDetails(AuthenticatedUser actor, UUID vehicleId, VehicleDetails details) {
        Vehicle vehicle = vehicles.findById(vehicleId).orElseThrow(VehicleNotFoundException::new);
        UUID ownerId = activeOwnerId(vehicleId);
        if (!permissions.canEditDetails(actor, vehicle, ownerId)) {
            throw new VehicleAccessDeniedException("You are not allowed to change this vehicle.");
        }
        Instant now = calendar.now();
        List<VehicleFieldChange> changes = vehicle.updateDetails(details, now, calendar.today());
        if (!changes.isEmpty()) {
            events.publishEvent(new VehicleEvents.VehicleDetailsChanged(vehicleId, actor.id(), changes, now));
        }
        return view(actor, vehicle, ownerId);
    }

    private UUID activeOwnerId(UUID vehicleId) {
        return ownerships.findByVehicleIdAndStatus(vehicleId, OwnershipStatus.ACTIVE)
                .map(VehicleOwnership::getUserId)
                .orElse(null);
    }

    private VehicleView view(AuthenticatedUser actor, Vehicle vehicle, UUID activeOwnerId) {
        return new VehicleView(
                vehicle.getId(),
                permissions.canSeeVin(actor, vehicle, activeOwnerId) ? vehicle.getVin() : null,
                vehicle.getLicensePlate(),
                vehicle.getMake(),
                vehicle.getModel(),
                vehicle.getModelYear(),
                vehicle.getFirstRegistrationDate(),
                vehicle.getStatus(),
                actor.id().equals(activeOwnerId),
                permissions.canEditDetails(actor, vehicle, activeOwnerId),
                vehicle.getCreatedAt(),
                vehicle.getUpdatedAt());
    }
}
