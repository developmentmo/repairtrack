package com.repairtrack.dispute.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.repairtrack.dispute.domain.DisputeStatus;
import com.repairtrack.dispute.domain.OwnershipDispute;

public interface OwnershipDisputeRepository extends JpaRepository<OwnershipDispute, UUID> {

    boolean existsByVehicleIdAndStatusIn(UUID vehicleId, Collection<DisputeStatus> statuses);

    boolean existsByVehicleIdAndClaimantIdAndStatusIn(UUID vehicleId, UUID claimantId,
                                                      Collection<DisputeStatus> statuses);

    long countByClaimantIdAndStatusIn(UUID claimantId, Collection<DisputeStatus> statuses);

    List<OwnershipDispute> findByClaimantIdOrOwnerIdOrderByCreatedAtDesc(UUID claimantId, UUID ownerId);

    List<OwnershipDispute> findByStatusInOrderByCreatedAtAsc(Collection<DisputeStatus> statuses);

    List<OwnershipDispute> findTop100ByStatusInOrderByDecidedAtDesc(Collection<DisputeStatus> statuses);
}
