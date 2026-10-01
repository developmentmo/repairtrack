package com.repairtrack.repair.application;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.repairtrack.garage.GarageAccessService;
import com.repairtrack.mileage.MileageHistory;
import com.repairtrack.mileage.MileageService;
import com.repairtrack.repair.application.Views.PartView;
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

    /** The complete history of a vehicle, newest first, including voided records. */
    @Transactional(readOnly = true)
    public List<RepairView> history(AuthenticatedUser actor, UUID vehicleId) {
        access.requireCanViewHistory(actor, vehicleId);
        return assembler.toViews(repairs.findByVehicleIdOrderByEventDateDescCreatedAtDesc(vehicleId));
    }

    @Transactional(readOnly = true)
    public RepairView get(AuthenticatedUser actor, UUID repairId) {
        RepairEvent event = repairs.findById(repairId).orElseThrow(RepairNotFoundException::new);
        access.requireCanViewHistory(actor, event.getVehicleId());
        return assembler.toView(event);
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
}
