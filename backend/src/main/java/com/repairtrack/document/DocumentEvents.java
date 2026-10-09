package com.repairtrack.document;

import java.time.Instant;
import java.util.UUID;

/** Events published by the document module. No file names (may contain personal data). */
public final class DocumentEvents {

    private DocumentEvents() {
    }

    public record DocumentUploaded(UUID documentId, UUID repairEventId, UUID vehicleId, DocumentType documentType,
                                   String mimeType, long fileSize, String sha256, UUID uploadedBy,
                                   Instant occurredAt) {
    }

    /** An upload was refused because the scanner found malware. Nothing was stored. */
    public record UploadRejectedAsMalware(UUID repairEventId, UUID uploadedBy, String signature, Instant occurredAt) {
    }

    /**
     * The periodic integrity sweep found a stored object that is missing or no longer matches its SHA-256.
     * {@code reason}: {@code MISSING} or {@code CHANGED}. Nothing is repaired automatically.
     */
    public record IntegrityCheckFailed(UUID documentId, UUID repairEventId, String reason, Instant occurredAt) {
    }

    /** An owner added a photo to their vehicle. {@code replacedPhotoId}: the previous photo, now REPLACED, or null. */
    public record VehiclePhotoUploaded(UUID photoId, UUID vehicleId, UUID replacedPhotoId, String mimeType,
                                       long fileSize, String sha256, UUID uploadedBy, Instant occurredAt) {
    }

    /** A vehicle photo was refused because the scanner found malware. Nothing was stored. */
    public record VehiclePhotoRejectedAsMalware(UUID vehicleId, UUID uploadedBy, String signature,
                                                Instant occurredAt) {
    }
}
