package com.repairtrack.repair.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.repairtrack.repair.domain.RepairCorrection;

public interface RepairCorrectionRepository extends JpaRepository<RepairCorrection, UUID> {

    List<RepairCorrection> findByRepairEventIdInOrderByCreatedAtAsc(Collection<UUID> repairEventIds);
}
