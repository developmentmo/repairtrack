package com.repairtrack.repair.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.repairtrack.repair.domain.RepairPart;

public interface RepairPartRepository extends JpaRepository<RepairPart, UUID> {

    List<RepairPart> findByRepairEventIdInOrderByCreatedAtAsc(Collection<UUID> repairEventIds);
}
