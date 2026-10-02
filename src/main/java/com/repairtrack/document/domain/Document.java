package com.repairtrack.document.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

import com.repairtrack.document.DocumentType;

/**
 * Metadata of a stored file. The bytes live in private object storage under {@link #storageKey};
 * {@link #sha256} is computed by the backend while receiving the upload and lets anyone detect
 * later changes to the stored object. Immutable: documents are never edited or deleted.
 */
@Entity
@Table(name = "document")
public class Document implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "repair_event_id", nullable = false, updatable = false)
    private UUID repairEventId;

    @Column(name = "vehicle_id", nullable = false, updatable = false)
    private UUID vehicleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, updatable = false, length = 30)
    private DocumentType documentType;

    @Column(name = "file_name", nullable = false, updatable = false, length = 200)
    private String fileName;

    @Column(name = "storage_key", nullable = false, updatable = false, length = 200)
    private String storageKey;

    @Column(name = "mime_type", nullable = false, updatable = false, length = 100)
    private String mimeType;

    @Column(name = "file_size", nullable = false, updatable = false)
    private long fileSize;

    /** Fixed-length CHAR(64) in the database (V14), so mapped as CHAR rather than the default VARCHAR. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "sha256", nullable = false, updatable = false, length = 64)
    private String sha256;

    @Column(name = "uploaded_by", nullable = false, updatable = false)
    private UUID uploadedBy;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt;

    @Transient
    private boolean isNew = true;

    protected Document() {
        // for JPA
    }

    public static Document create(UUID id, UUID repairEventId, UUID vehicleId, DocumentType documentType,
                                  String fileName, String storageKey, String mimeType, long fileSize, String sha256,
                                  UUID uploadedBy, Instant uploadedAt) {
        if (fileSize <= 0) {
            throw new IllegalArgumentException("fileSize must be positive");
        }
        if (sha256 == null || !sha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("sha256 must be 64 lower-case hex characters");
        }
        Document document = new Document();
        document.id = Objects.requireNonNull(id, "id");
        document.repairEventId = Objects.requireNonNull(repairEventId, "repairEventId");
        document.vehicleId = Objects.requireNonNull(vehicleId, "vehicleId");
        document.documentType = Objects.requireNonNull(documentType, "documentType");
        document.fileName = Objects.requireNonNull(fileName, "fileName");
        document.storageKey = Objects.requireNonNull(storageKey, "storageKey");
        document.mimeType = Objects.requireNonNull(mimeType, "mimeType");
        document.fileSize = fileSize;
        document.sha256 = sha256;
        document.uploadedBy = Objects.requireNonNull(uploadedBy, "uploadedBy");
        document.uploadedAt = Objects.requireNonNull(uploadedAt, "uploadedAt");
        return document;
    }

    /** Storage keys contain only IDs: no user input, no personal data. */
    public static String storageKeyFor(UUID repairEventId, UUID documentId) {
        return "repair-events/" + repairEventId + "/" + documentId;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    @PostPersist
    void markPersisted() {
        isNew = false;
    }

    public UUID getRepairEventId() {
        return repairEventId;
    }

    public UUID getVehicleId() {
        return vehicleId;
    }

    public DocumentType getDocumentType() {
        return documentType;
    }

    public String getFileName() {
        return fileName;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getMimeType() {
        return mimeType;
    }

    public long getFileSize() {
        return fileSize;
    }

    public String getSha256() {
        return sha256;
    }

    public UUID getUploadedBy() {
        return uploadedBy;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }
}
