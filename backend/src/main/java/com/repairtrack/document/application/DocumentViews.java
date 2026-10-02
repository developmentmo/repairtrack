package com.repairtrack.document.application;

import java.net.URL;
import java.time.Instant;
import java.util.UUID;

import com.repairtrack.document.DocumentType;
import com.repairtrack.document.domain.Document;

public final class DocumentViews {

    private DocumentViews() {
    }

    /** {@code repairVerificationRaised}: the upload raised the repair record to DOCUMENTED. */
    public record DocumentView(UUID id, UUID repairEventId, DocumentType documentType, String fileName,
                               String mimeType, long fileSize, String sha256, Instant uploadedAt,
                               boolean repairVerificationRaised) {

        static DocumentView of(Document document, boolean repairVerificationRaised) {
            return new DocumentView(document.getId(), document.getRepairEventId(), document.getDocumentType(),
                    document.getFileName(), document.getMimeType(), document.getFileSize(), document.getSha256(),
                    document.getUploadedAt(), repairVerificationRaised);
        }
    }

    public record DownloadView(DocumentView document, URL downloadUrl, Instant expiresAt) {
    }

    /** {@code actualSha256} is null when the stored object is missing. */
    public record IntegrityView(UUID documentId, String expectedSha256, String actualSha256, boolean intact,
                                Instant checkedAt) {
    }
}
