package com.repairtrack.vehicle;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.repairtrack.vehicle.application.VehicleNotFoundException;
import com.repairtrack.vehicle.domain.Vehicle;
import com.repairtrack.vehicle.infrastructure.VehicleOwnershipRepository;
import com.repairtrack.vehicle.infrastructure.VehicleRepository;

/** Read-only vehicle lookups for other modules. */
@Service
public class VehicleDirectory {

    private final VehicleRepository vehicles;
    private final VehicleOwnershipRepository ownerships;

    public VehicleDirectory(VehicleRepository vehicles, VehicleOwnershipRepository ownerships) {
        this.vehicles = vehicles;
        this.ownerships = ownerships;
    }

    /** For the public report. No authorization here: the sharing module has validated the share. */
    @Transactional(readOnly = true)
    public VehiclePublicProfile publicProfile(UUID vehicleId) {
        Vehicle vehicle = vehicles.findById(vehicleId).orElseThrow(VehicleNotFoundException::new);
        return new VehiclePublicProfile(vehicle.getMake(), vehicle.getModel(), vehicle.getModelYear(),
                vehicle.getFirstRegistrationDate(), vehicle.getLicensePlate(), ownerships.countByVehicleId(vehicleId));
    }

    @Transactional(readOnly = true)
    public List<VehicleSummary> findSummaries(Collection<UUID> vehicleIds) {
        if (vehicleIds.isEmpty()) {
            return List.of();
        }
        return vehicles.findAllById(vehicleIds).stream().map(VehicleDirectory::summary).toList();
    }

    /** The garage that registered the vehicle on behalf of a customer, if any. */
    @Transactional(readOnly = true)
    public Optional<UUID> registeringGarageId(UUID vehicleId) {
        Vehicle vehicle = vehicles.findById(vehicleId).orElseThrow(VehicleNotFoundException::new);
        return Optional.ofNullable(vehicle.getRegisteredByGarageId());
    }

    @Transactional(readOnly = true)
    public List<UUID> idsRegisteredByGarage(UUID garageId) {
        return vehicles.findByRegisteredByGarageId(garageId).stream().map(Vehicle::getId).toList();
    }

    private static VehicleSummary summary(Vehicle vehicle) {
        return new VehicleSummary(vehicle.getId(), vehicle.getLicensePlate(), vehicle.getMake(), vehicle.getModel(),
                vehicle.getModelYear());
    }
}
