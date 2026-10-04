package com.repairtrack.document;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.repairtrack.document.application.IncomingFile;
import com.repairtrack.document.application.MalwareDetectedException;
import com.repairtrack.document.application.UploadPipeline;
import com.repairtrack.document.domain.DetectedFileType;
import com.repairtrack.document.domain.FileNames;
import com.repairtrack.document.infrastructure.DocumentStorage;
import com.repairtrack.document.infrastructure.MalwareScanner;
import com.repairtrack.document.infrastructure.StorageProperties;

/**
 * Private evidence files for other modules (ownership disputes): the same checks as history documents (size,
 * PDF/JPEG/PNG by content, malware scan, SHA-256), stored in the same private bucket, but not part of a vehicle's
 * history and never shared. The caller keeps the metadata and does its own authorization.
 */
@Service
public class EvidenceFiles {

    private static final Logger log = LoggerFactory.getLogger(EvidenceFiles.class);

    /** What the caller stores. {@code fileName} is sanitized; show it only to people allowed to see the file. */
    public record StoredEvidence(String storageKey, String fileName, String mimeType, long size, String sha256) {
    }

    @FunctionalInterface
    public interface Content {

        /** A fresh stream over the complete content; the caller closes it. Called more than once. */
        InputStream open() throws IOException;
    }

    private final UploadPipeline pipeline;
    private final DocumentStorage storage;
    private final StorageProperties properties;

    public EvidenceFiles(DocumentStorage storage, MalwareScanner scanner, StorageProperties properties) {
        this.pipeline = new UploadPipeline(storage, scanner, properties);
        this.storage = storage;
        this.properties = properties;
    }

    /**
     * Checks and stores a file under {@code keyPrefix}. Call inside the caller's transaction: on rollback the object
     * is removed again. Throws the document module's errors ({@code EMPTY_FILE}, {@code FILE_TOO_LARGE},
     * {@code UNSUPPORTED_FILE_TYPE}, {@code MALWARE_DETECTED}, {@code SCANNER_UNAVAILABLE}).
     */
    public StoredEvidence store(String keyPrefix, String fileName, long size, Content content) {
        IncomingFile file = new IncomingFile(fileName, size, content::open);
        DetectedFileType type = pipeline.check(file);
        MalwareScanner.ScanResult result = pipeline.scan(file);
        if (!result.clean()) {
            log.warn("Evidence upload rejected: malware '{}' ({})", result.signature(), keyPrefix);
            throw new MalwareDetectedException();
        }
        String key = keyPrefix + "/" + UUID.randomUUID();
        String sha256 = pipeline.store(key, file, type);
        return new StoredEvidence(key, FileNames.sanitize(fileName, type), type.mimeType(), size, sha256);
    }

    /**
     * Integrity check of a stored evidence file against the SHA-256 taken at upload.
     *
     * @return {@code MISSING}, {@code CHANGED} or {@code UNREADABLE}; empty when intact
     */
    public Optional<String> verify(String storageKey, String sha256) {
        return pipeline.verify(storageKey, sha256);
    }

    /** Short-lived download link; the bucket itself stays private. */
    public URL presignDownload(String storageKey, String fileName, String mimeType) {
        return storage.presignDownload(storageKey, fileName, mimeType, properties.presignedUrlTtl());
    }

    public Duration presignedUrlTtl() {
        return properties.presignedUrlTtl();
    }
}
