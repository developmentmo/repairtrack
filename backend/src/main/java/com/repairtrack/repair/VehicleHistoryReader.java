package com.repairtrack.repair;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.repairtrack.repair.application.RepairQueryService;
import com.repairtrack.repair.domain.RepairStatus;

/**
 * Unauthorized read access to a vehicle's full history, for modules that have performed their own
 * access check (the sharing module after validating a share token). Never call this from code that
 * serves an arbitrary user without such a check.
 */
@Service
public class VehicleHistoryReader {

    private final RepairQueryService queryService;

    public VehicleHistoryReader(RepairQueryService queryService) {
        this.queryService = queryService;
    }

    /** Newest first, including voided records. */
    @Transactional(readOnly = true)
    public List<VehicleHistory.Entry> history(UUID vehicleId) {
        return queryService.historyWithoutAuthorization(vehicleId).stream()
                .map(v -> new VehicleHistory.Entry(v.id(), v.eventType(), v.eventDate(), v.mileage(), v.title(),
                        v.description(), v.sourceType(), v.verificationStatus(),
                        v.status() == RepairStatus.VOIDED, v.voidReason(), v.voidedAt(),
                        v.garage(),
                        v.parts().stream().map(p -> new VehicleHistory.Part(p.partNumber(), p.brand(), p.description(),
                                p.quantity())).toList(),
                        v.corrections().stream().map(c -> new VehicleHistory.Correction(c.field().name(),
                                c.oldValue(), c.newValue(), c.reason(), c.correctedByGarage(), c.correctedAt())).toList(),
                        v.enteredDuringRevokedOwnership()))
                .toList();
    }
}
