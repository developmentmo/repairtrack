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
}
