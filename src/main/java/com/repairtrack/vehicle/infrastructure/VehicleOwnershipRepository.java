package com.repairtrack.vehicle.infrastructure;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.repairtrack.vehicle.domain.OwnershipStatus;
import com.repairtrack.vehicle.domain.VehicleOwnership;

public interface VehicleOwnershipRepository extends JpaRepository<VehicleOwnership, UUID> {

    Optional<VehicleOwnership> findByVehicleIdAndStatus(UUID vehicleId, OwnershipStatus status);

    boolean existsByVehicleIdAndUserIdAndStatus(UUID vehicleId, UUID userId, OwnershipStatus status);

    List<VehicleOwnership> findByUserIdAndStatus(UUID userId, OwnershipStatus status);

    long countByVehicleId(UUID vehicleId);

    /** End date of the most recent previous ownership; a new ownership may not start before it. */
    @Query("select max(o.endDate) from VehicleOwnership o where o.vehicleId = :vehicleId")
    Optional<LocalDate> findLatestEndDate(@Param("vehicleId") UUID vehicleId);
}
