package com.repairtrack.dispute.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A file one party submitted. Append-only; only system admins can download it. */
@Entity
@Table(name = "dispute_evidence")
public class DisputeEvidence {

    @Id
    private UUID id;

    @Column(name = "dispute_id", nullable = false, updatable = false)
    private UUID disputeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "party", nullable = false, updatable = false, length = 20)
    private DisputeParty party;

    @Column(name = "submitted_by", nullable = false, updatable = false)
    private UUID submittedBy;

    @Column(name = "file_name", nullable = false, updatable = false, length = 200)
    private String fileName;

    @Column(name = "storage_key", nullable = false, updatable = false, length = 200)
    private String storageKey;

    @Column(name = "mime_type", nullable = false, updatable = false, length = 100)
    private String mimeType;

    @Column(name = "file_size", nullable = false, updatable = false)
    private long fileSize;

    @Column(name = "sha256", nullable = false, updatable = false, length = 64)
    private String sha256;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt;

    protected DisputeEvidence() {
        // for JPA
    }

    public static DisputeEvidence of(UUID disputeId, DisputeParty party, UUID submittedBy, String fileName,
                                     String storageKey, String mimeType, long fileSize, String sha256, Instant now) {
        DisputeEvidence evidence = new DisputeEvidence();
        evidence.id = UUID.randomUUID();
        evidence.disputeId = Objects.requireNonNull(disputeId, "disputeId");
        evidence.party = Objects.requireNonNull(party, "party");
        evidence.submittedBy = Objects.requireNonNull(submittedBy, "submittedBy");
        evidence.fileName = Objects.requireNonNull(fileName, "fileName");
        evidence.storageKey = Objects.requireNonNull(storageKey, "storageKey");
        evidence.mimeType = Objects.requireNonNull(mimeType, "mimeType");
        evidence.fileSize = fileSize;
        evidence.sha256 = Objects.requireNonNull(sha256, "sha256");
        evidence.uploadedAt = Objects.requireNonNull(now, "now");
        return evidence;
    }

    public UUID getId() {
        return id;
    }

    public UUID getDisputeId() {
        return disputeId;
    }

    public DisputeParty getParty() {
        return party;
    }

    public UUID getSubmittedBy() {
        return submittedBy;
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

    public Instant getUploadedAt() {
        return uploadedAt;
    }
}
