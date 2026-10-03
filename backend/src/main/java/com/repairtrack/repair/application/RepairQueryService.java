package com.repairtrack.repair.application;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.repairtrack.garage.GarageAccessService;
import com.repairtrack.mileage.MileageHistory;
import com.repairtrack.mileage.MileageService;
import com.repairtrack.repair.application.Views.PartView;
import com.repairtrack.repair.application.Views.RepairEntry;
import com.repairtrack.repair.application.Views.RepairPermissions;
import com.repairtrack.repair.application.Views.RepairView;
import com.repairtrack.repair.domain.RepairEvent;
import com.repairtrack.repair.infrastructure.RepairEventRepository;
import com.repairtrack.security.AuthenticatedUser;
import com.repairtrack.vehicle.VehicleDirectory;
import com.repairtrack.vehicle.VehicleSummary;

/** Read side of the vehicle history. Every method checks the caller may see the vehicle's history. */
@Service
public class RepairQueryService {

    private final RepairEventRepository repairs;
    private final RepairAccessPolicy access;
    private final RepairViewAssembler assembler;
    private final MileageService mileage;
    private final GarageAccessService garageAccess;
    private final VehicleDirectory vehicleDirectory;

    public RepairQueryService(RepairEventRepository repairs, RepairAccessPolicy access, RepairViewAssembler assembler,
                              MileageService mileage, GarageAccessService garageAccess,
                              VehicleDirectory vehicleDirectory) {
        this.repairs = repairs;
        this.access = access;
        this.assembler = assembler;
        this.mileage = mileage;
        this.garageAccess = garageAccess;
        this.vehicleDirectory = vehicleDirectory;
    }

    /** The complete history of a vehicle, newest first, including voided records, with the caller's permissions. */
    @Transactional(readOnly = true)
    public List<RepairEntry> history(AuthenticatedUser actor, UUID vehicleId) {
        access.requireCanViewHistory(actor, vehicleId);
        return withPermissions(actor, repairs.findByVehicleIdOrderByEventDateDescCreatedAtDesc(vehicleId));
    }

    /** For {@code VehicleHistoryReader} only: the caller has authorized access by other means. */
    @Transactional(readOnly = true)
    public List<RepairView> historyWithoutAuthorization(UUID vehicleId) {
        return assembler.toViews(repairs.findByVehicleIdOrderByEventDateDescCreatedAtDesc(vehicleId));
    }

    @Transactional(readOnly = true)
    public RepairView get(AuthenticatedUser actor, UUID repairId) {
        RepairEvent event = repairs.findById(repairId).orElseThrow(RepairNotFoundException::new);
        access.requireCanViewHistory(actor, event.getVehicleId());
        return assembler.toView(event);
    }

    /** One record with the caller's permissions. */
    @Transactional(readOnly = true)
    public RepairEntry entry(AuthenticatedUser actor, UUID repairId) {
        RepairEvent event = repairs.findById(repairId).orElseThrow(RepairNotFoundException::new);
        access.requireCanViewHistory(actor, event.getVehicleId());
        return withPermissions(actor, List.of(event)).getFirst();
    }

    /** The caller's permissions on a record they have just changed. */
    @Transactional(readOnly = true)
    public RepairPermissions permissions(AuthenticatedUser actor, UUID repairId) {
        RepairEvent event = repairs.findById(repairId).orElseThrow(RepairNotFoundException::new);
        return access.permissionsFor(actor).apply(event);
    }

    @Transactional(readOnly = true)
    public List<PartView> parts(AuthenticatedUser actor, UUID repairId) {
        return get(actor, repairId).parts();
    }

    @Transactional(readOnly = true)
    public MileageHistory mileage(AuthenticatedUser actor, UUID vehicleId) {
        access.requireCanViewHistory(actor, vehicleId);
        return mileage.history(vehicleId);
    }

    /** Vehicles a garage has registered or recorded work on. For its members (and system admins). */
    @Transactional(readOnly = true)
    public List<VehicleSummary> garageVehicles(AuthenticatedUser actor, UUID garageId) {
        garageAccess.requireMemberOrSystemAdmin(actor, garageId);
        Set<UUID> ids = new LinkedHashSet<>(repairs.findVehicleIdsByGarageId(garageId));
        ids.addAll(vehicleDirectory.idsRegisteredByGarage(garageId));
        return vehicleDirectory.findSummaries(ids).stream()
                .sorted(Comparator.comparing(VehicleSummary::make, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(VehicleSummary::model, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private List<RepairEntry> withPermissions(AuthenticatedUser actor, List<RepairEvent> events) {
        Function<RepairEvent, RepairPermissions> permissionsOf = access.permissionsFor(actor);
        Map<UUID, RepairPermissions> byId = new HashMap<>();
        events.forEach(event -> byId.put(event.getId(), permissionsOf.apply(event)));
        return assembler.toViews(events).stream()
                .map(view -> new RepairEntry(view, byId.get(view.id())))
                .toList();
    }
}
