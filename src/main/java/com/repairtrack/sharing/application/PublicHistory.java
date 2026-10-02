package com.repairtrack.sharing.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.repairtrack.document.DocumentType;
import com.repairtrack.garage.GarageVerificationStatus;
import com.repairtrack.repair.RepairEventType;
import com.repairtrack.verification.SourceType;
import com.repairtrack.verification.VerificationStatus;

/**
 * The public vehicle report: intentionally limited. It contains NO internal IDs, no VIN, no owner
 * or user identities, no file names; garages appear by name (public business information).
 */
public final class PublicHistory {

    private PublicHistory() {
    }

    public record Report(
            Vehicle vehicle,
            Summary summary,
            List<Entry> history,
            Mileage mileage,
            boolean documentsDownloadable,
            Instant generatedAt,
            Instant linkValidUntil
    ) {
    }

    public record Vehicle(String make, String model, Integer modelYear, LocalDate firstRegistrationDate,
                          String licensePlate, long registeredOwnerCount) {
    }

    public record Summary(int totalRecords, int voidedRecords, Map<VerificationStatus, Long> recordsByVerification,
                          LocalDate firstEventDate, LocalDate lastEventDate, Integer lastRecordedMileage,
                          int mileageInconsistencies, int documentCount) {
    }

    public record Entry(
            RepairEventType eventType,
            LocalDate eventDate,
            int mileage,
            String title,
            String description,
            SourceType sourceType,
            VerificationStatus verificationStatus,
            boolean voided,
            String voidReason,
            Garage garage,
            List<Part> parts,
            List<Correction> corrections,
            List<Document> documents
    ) {
    }

    public record Garage(String name, String city, GarageVerificationStatus verificationStatus) {
    }

    public record Part(String partNumber, String brand, String description, int quantity) {
    }

    /** {@code correctedBy}: "OWNER" or the garage's name. */
    public record Correction(String field, String originalValue, String correctedValue, String reason,
                             String correctedBy, Instant correctedAt) {
    }

    /**
     * {@code reference} (the document's SHA-256) is only present when the link allows downloads; it is
     * both the download reference and the integrity fingerprint of the file.
     */
    public record Document(DocumentType documentType, String mimeType, long fileSize, Instant uploadedAt,
                           boolean downloadable, String reference) {
    }

    public record Mileage(List<Reading> readings, List<Inconsistency> inconsistencies) {
    }

    public record Reading(LocalDate date, int mileage, SourceType sourceType) {
    }

    public record Inconsistency(String message, Reading earlier, Reading later) {
    }

    public record DocumentLink(String downloadUrl, Instant expiresAt) {
    }
}
