package com.repairtrack.dispute.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.repairtrack.dispute.domain.DisputeEvidence;
import com.repairtrack.dispute.domain.DisputeParty;

public interface DisputeEvidenceRepository extends JpaRepository<DisputeEvidence, UUID> {

    List<DisputeEvidence> findByDisputeIdInOrderByUploadedAtAsc(Collection<UUID> disputeIds);

    long countByDisputeIdAndParty(UUID disputeId, DisputeParty party);
}
