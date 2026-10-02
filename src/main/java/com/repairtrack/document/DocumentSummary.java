package com.repairtrack.document;

import java.time.Instant;
import java.util.UUID;

/**
 * Document metadata for other modules. No file name (may contain personal data) and no uploader.
 * {@code sha256} doubles as the public reference of a document in a shared report.
 */
public record DocumentSummary(UUID repairEventId, DocumentType documentType, String mimeType, long fileSize,
                              String sha256, Instant uploadedAt) {
}
