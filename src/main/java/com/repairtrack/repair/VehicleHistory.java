package com.repairtrack.repair;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.repairtrack.garage.GarageSummary;
import com.repairtrack.verification.SourceType;
import com.repairtrack.verification.VerificationStatus;

/**
 * Read model of a vehicle's history for other modules (the public report). {@code repairId} is for
 * joining with other data inside the backend; consumers must not expose it publicly.
 */
public final class VehicleHistory {

    private VehicleHistory() {
    }

    public record Entry(
            UUID repairId,
            RepairEventType eventType,
            LocalDate eventDate,
            int mileage,
            String title,
            String description,
            SourceType sourceType,
            VerificationStatus verificationStatus,
            boolean voided,
            String voidReason,
            Instant voidedAt,
            GarageSummary garage,
            List<Part> parts,
            List<Correction> corrections
    ) {
    }

    public record Part(String partNumber, String brand, String description, int quantity) {
    }

    /** {@code correctedByGarage} null: corrected by the owner. */
    public record Correction(String field, String originalValue, String correctedValue, String reason,
                             GarageSummary correctedByGarage, Instant correctedAt) {
    }
}
