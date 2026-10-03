package com.repairtrack.dispute;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.repairtrack.dispute.domain.DisputeStatus;
import com.repairtrack.dispute.infrastructure.OwnershipDisputeRepository;

/** Read-only dispute lookups for other modules (sharing). */
@Service
public class DisputeDirectory {

    private final OwnershipDisputeRepository disputes;

    public DisputeDirectory(OwnershipDisputeRepository disputes) {
        this.disputes = disputes;
    }

    /** True while any dispute about the vehicle's ownership is undecided. */
    @Transactional(readOnly = true)
    public boolean isUnderDispute(UUID vehicleId) {
        return disputes.existsByVehicleIdAndStatusIn(vehicleId, DisputeStatus.UNDECIDED);
    }
}
