package com.repairtrack.verification.domain;

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

import org.springframework.data.domain.Persistable;

import com.repairtrack.verification.Provenance;
import com.repairtrack.verification.SourceType;
import com.repairtrack.verification.VerificationChange;
import com.repairtrack.verification.VerificationMethod;
import com.repairtrack.verification.VerificationStatus;

/** Immutable row of the {@code verification} table. */
@Entity
@Table(name = "verification")
public class VerificationRecord implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "repair_event_id", nullable = false, updatable = false)
    private UUID repairEventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_source_type", nullable = false, updatable = false, length = 20)
    private SourceType previousSourceType;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", nullable = false, updatable = false, length = 20)
    private VerificationStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_source_type", nullable = false, updatable = false, length = 20)
    private SourceType newSourceType;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, updatable = false, length = 20)
    private VerificationStatus newStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, updatable = false, length = 30)
    private VerificationMethod method;

    @Column(name = "evidence_id", updatable = false)
    private UUID evidenceId;

    @Column(name = "changed_by", nullable = false, updatable = false)
    private UUID changedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Transient
    private boolean isNew = true;

    protected VerificationRecord() {
        // for JPA
    }

    public static VerificationRecord of(UUID repairEventId, Provenance from, Provenance to, VerificationMethod method,
                                        UUID evidenceId, UUID changedBy, Instant now) {
        VerificationRecord record = new VerificationRecord();
        record.id = UUID.randomUUID();
        record.repairEventId = Objects.requireNonNull(repairEventId, "repairEventId");
        record.previousSourceType = from.sourceType();
        record.previousStatus = from.verificationStatus();
        record.newSourceType = to.sourceType();
        record.newStatus = to.verificationStatus();
        record.method = Objects.requireNonNull(method, "method");
        record.evidenceId = evidenceId;
        record.changedBy = Objects.requireNonNull(changedBy, "changedBy");
        record.createdAt = Objects.requireNonNull(now, "now");
        return record;
    }

    public VerificationChange toChange() {
        return new VerificationChange(id, repairEventId, new Provenance(previousSourceType, previousStatus),
                new Provenance(newSourceType, newStatus), method, evidenceId, changedBy, createdAt);
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
}
