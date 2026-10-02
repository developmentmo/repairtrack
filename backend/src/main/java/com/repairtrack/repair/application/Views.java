package com.repairtrack.repair.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.repairtrack.garage.GarageSummary;
import com.repairtrack.mileage.MileageAnomaly;
import com.repairtrack.repair.RepairEventType;
import com.repairtrack.repair.domain.CorrectableField;
import com.repairtrack.repair.domain.RepairStatus;
import com.repairtrack.verification.SourceType;
import com.repairtrack.verification.VerificationStatus;

/** Read models of the repair module. They never contain user identities. */
public final class Views {

    private Views() {
    }

    /** {@code garage} is null for owner records. */
    public record RepairView(
            UUID id,
            UUID vehicleId,
            RepairEventType eventType,
            LocalDate eventDate,
            int mileage,
            String title,
            String description,
            SourceType sourceType,
            VerificationStatus verificationStatus,
            RepairStatus status,
            GarageSummary garage,
            List<PartView> parts,
            List<CorrectionView> corrections,
            Instant voidedAt,
            String voidReason,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record PartView(UUID id, String partNumber, String brand, String description, int quantity) {
    }

    /** {@code correctedByGarage} null: corrected by the owner. */
    public record CorrectionView(CorrectableField field, String oldValue, String newValue, String reason,
                                 GarageSummary correctedByGarage, Instant correctedAt) {
    }

    /** Result of a mutation that may produce non-blocking warnings (mileage inconsistencies). */
    public record RepairResult(RepairView repair, List<MileageAnomaly> warnings) {
    }
}
