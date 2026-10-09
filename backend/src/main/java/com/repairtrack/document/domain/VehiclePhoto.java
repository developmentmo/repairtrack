package com.repairtrack.document.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A photo an owner added to their vehicle. Private to the owner who uploaded it, and only while they own the
 * vehicle; never part of the history or the public report. Never deleted: a new upload marks the previous photo
 * {@link Status#REPLACED}, and its stored object stays.
 */
@Entity
@Table(name = "vehicle_photo")
public class VehiclePhoto {

    public enum Status {
        ACTIVE,
        REPLACED
    }

    @Id
    private UUID id;

    @Column(name = "vehicle_id", nullable = false, updatable = false)
    private UUID vehicleId;

    @Column(name = "uploaded_by", nullable = false, updatable = false)
    private UUID uploadedBy;

    @Column(name = "storage_key", nullable = false, updatable = false, length = 200)
    private String storageKey;

    @Column(name = "mime_type", nullable = false, updatable = false, length = 100)
    private String mimeType;

    @Column(name = "file_size", nullable = false, updatable = false)
    private long fileSize;

    /** Fixed-length CHAR(64) in the database (V19), so mapped as CHAR rather than the default VARCHAR. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "sha256", nullable = false, updatable = false, length = 64)
    private String sha256;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status;

    @Column(name = "replaced_at")
    private Instant replacedAt;

    @Column(name = "replaced_by_id")
    private UUID replacedById;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected VehiclePhoto() {
        // for JPA
    }

    public static VehiclePhoto create(UUID id, UUID vehicleId, UUID uploadedBy, String storageKey, String mimeType,
                                      long fileSize, String sha256, Instant uploadedAt) {
        if (fileSize <= 0) {
            throw new IllegalArgumentException("fileSize must be positive");
        }
        if (sha256 == null || !sha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("sha256 must be 64 lower-case hex characters");
        }
        VehiclePhoto photo = new VehiclePhoto();
        photo.id = Objects.requireNonNull(id, "id");
        photo.vehicleId = Objects.requireNonNull(vehicleId, "vehicleId");
        photo.uploadedBy = Objects.requireNonNull(uploadedBy, "uploadedBy");
        photo.storageKey = Objects.requireNonNull(storageKey, "storageKey");
        photo.mimeType = Objects.requireNonNull(mimeType, "mimeType");
        photo.fileSize = fileSize;
        photo.sha256 = sha256;
        photo.uploadedAt = Objects.requireNonNull(uploadedAt, "uploadedAt");
        photo.status = Status.ACTIVE;
        return photo;
    }

    /** Storage keys contain only IDs: no user input, no personal data. */
    public static String storageKeyFor(UUID vehicleId, UUID photoId) {
        return "vehicles/" + vehicleId + "/photos/" + photoId;
    }

    public void replaceWith(UUID successorId, Instant now) {
        if (status != Status.ACTIVE) {
            throw new IllegalStateException("Photo already replaced");
        }
        replacedById = Objects.requireNonNull(successorId, "successorId");
        replacedAt = Objects.requireNonNull(now, "now");
        status = Status.REPLACED;
    }

    public UUID getId() {
        return id;
    }

    public UUID getVehicleId() {
        return vehicleId;
    }

    public UUID getUploadedBy() {
        return uploadedBy;
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

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getReplacedAt() {
        return replacedAt;
    }

    public UUID getReplacedById() {
        return replacedById;
    }
}
