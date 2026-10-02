package com.repairtrack.document;

import java.net.URL;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.repairtrack.document.domain.DetectedFileType;
import com.repairtrack.document.domain.Document;
import com.repairtrack.document.infrastructure.DocumentRepository;
import com.repairtrack.document.infrastructure.DocumentStorage;
import com.repairtrack.document.infrastructure.StorageProperties;

/**
 * Unauthorized document lookups for modules that performed their own access check (the sharing
 * module after validating a share that allows documents).
 */
@Service
public class DocumentDirectory {

    private final DocumentRepository documents;
    private final DocumentStorage storage;
    private final StorageProperties properties;

    public DocumentDirectory(DocumentRepository documents, DocumentStorage storage, StorageProperties properties) {
        this.documents = documents;
        this.storage = storage;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public List<DocumentSummary> forVehicle(UUID vehicleId) {
        return documents.findByVehicleIdOrderByUploadedAtAsc(vehicleId).stream()
                .map(d -> new DocumentSummary(d.getRepairEventId(), d.getDocumentType(), d.getMimeType(),
                        d.getFileSize(), d.getSha256(), d.getUploadedAt()))
                .toList();
    }

    /** Presigned download link for the vehicle's document with this content hash, if any. */
    @Transactional(readOnly = true)
    public Optional<URL> presignForVehicle(UUID vehicleId, String sha256) {
        if (sha256 == null || !sha256.matches("[0-9a-f]{64}")) {
            return Optional.empty();
        }
        return documents.findFirstByVehicleIdAndSha256OrderByUploadedAtAsc(vehicleId, sha256)
                .map(d -> storage.presignDownload(d.getStorageKey(), publicFileName(d), d.getMimeType(),
                        properties.presignedUrlTtl()));
    }

    public Duration presignedUrlTtl() {
        return properties.presignedUrlTtl();
    }

    /** The original file name may contain personal data; shared downloads get a neutral name. */
    private static String publicFileName(Document document) {
        String extension = DetectedFileType.fromMimeType(document.getMimeType())
                .map(DetectedFileType::extension).orElse("bin");
        return document.getDocumentType().name().toLowerCase(Locale.ROOT) + "-" + document.getUploadedAt()
                .toString().substring(0, 10) + "." + extension;
    }
}
