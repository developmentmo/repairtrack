package com.repairtrack.repair.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.repairtrack.repair.domain.RepairEvent;

public interface RepairEventRepository extends JpaRepository<RepairEvent, UUID> {

    /** Full history including voided records, newest first. */
    List<RepairEvent> findByVehicleIdOrderByEventDateDescCreatedAtDesc(UUID vehicleId);

    boolean existsByVehicleIdAndGarageIdIn(UUID vehicleId, Collection<UUID> garageIds);

    @Query("select distinct r.vehicleId from RepairEvent r where r.garageId = :garageId")
    List<UUID> findVehicleIdsByGarageId(@Param("garageId") UUID garageId);
}
