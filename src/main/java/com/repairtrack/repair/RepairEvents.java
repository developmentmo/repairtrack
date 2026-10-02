package com.repairtrack.repair;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.repairtrack.verification.SourceType;
import com.repairtrack.verification.VerificationStatus;

/**
 * Events published by the repair module, synchronously and inside the mutating transaction
 * (the audit module relies on that for atomicity). They contain IDs and business values only,
 * so they can later be written to an outbox unchanged.
 */
public final class RepairEvents {

    private RepairEvents() {
    }

    /** Values of a repair event at a point in time (audit "newValue" on creation). */
    public record RepairSnapshot(RepairEventType eventType, LocalDate eventDate, int mileage, String title,
                                 SourceType sourceType, VerificationStatus verificationStatus) {
    }

    public record RepairCreated(UUID repairId, UUID vehicleId, UUID garageId, UUID createdBy,
                                RepairSnapshot snapshot, Instant occurredAt) {
    }

    public record FieldCorrection(String field, String oldValue, String newValue) {
    }

    public record RepairCorrected(UUID repairId, UUID vehicleId, UUID correctedBy, UUID garageId,
                                  List<FieldCorrection> corrections, String reason, Instant occurredAt) {
    }

    public record RepairVoided(UUID repairId, UUID vehicleId, UUID voidedBy, String reason, Instant occurredAt) {
    }

    /** Provenance raised after creation, e.g. OWNER/UNVERIFIED -> OWNER_DOCUMENT/DOCUMENTED. */
    public record RepairProvenanceChanged(UUID repairId, UUID vehicleId, SourceType fromSource,
                                          VerificationStatus fromStatus, SourceType toSource,
                                          VerificationStatus toStatus, UUID evidenceDocumentId, UUID changedBy,
                                          Instant occurredAt) {
    }

    public record RepairPartAdded(UUID repairId, UUID partId, UUID addedBy, String partNumber, String brand,
                                  String description, int quantity, Instant occurredAt) {
    }
}
