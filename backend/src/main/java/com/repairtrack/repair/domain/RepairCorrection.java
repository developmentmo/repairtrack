package com.repairtrack.repair.domain;

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

/**
 * One corrected field of a repair event: original value, corrected value, why, by whom, when.
 * Immutable and append-only; together these rows reconstruct every earlier version.
 */
@Entity
@Table(name = "repair_correction")
public class RepairCorrection implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "repair_event_id", nullable = false, updatable = false)
    private UUID repairEventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "field", nullable = false, updatable = false, length = 30)
    private CorrectableField field;

    @Column(name = "old_value", updatable = false, length = 5000)
    private String oldValue;

    @Column(name = "new_value", updatable = false, length = 5000)
    private String newValue;

    @Column(name = "reason", nullable = false, updatable = false, length = 500)
    private String reason;

    @Column(name = "corrected_by", nullable = false, updatable = false)
    private UUID correctedBy;

    /** Garage on whose behalf the correction was made; null when the owner corrected. */
    @Column(name = "corrected_by_garage_id", updatable = false)
    private UUID correctedByGarageId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Immutable rows have no @Version; this tells Spring Data to INSERT instead of merging. */
    @Transient
    private boolean isNew = true;

    protected RepairCorrection() {
        // for JPA
    }

    static RepairCorrection of(UUID repairEventId, CorrectableField field, Object oldValue, Object newValue,
                               String reason, UUID correctedBy, UUID correctedByGarageId, Instant now) {
        RepairCorrection correction = new RepairCorrection();
        correction.id = UUID.randomUUID();
        correction.repairEventId = Objects.requireNonNull(repairEventId, "repairEventId");
        correction.field = Objects.requireNonNull(field, "field");
        correction.oldValue = oldValue == null ? null : oldValue.toString();
        correction.newValue = newValue == null ? null : newValue.toString();
        correction.reason = Objects.requireNonNull(reason, "reason");
        correction.correctedBy = Objects.requireNonNull(correctedBy, "correctedBy");
        correction.correctedByGarageId = correctedByGarageId;
        correction.createdAt = Objects.requireNonNull(now, "now");
        return correction;
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

    public CorrectableField getField() {
        return field;
    }

    public String getOldValue() {
        return oldValue;
    }

    public String getNewValue() {
        return newValue;
    }

    public String getReason() {
        return reason;
    }

    public UUID getCorrectedBy() {
        return correctedBy;
    }

    public UUID getCorrectedByGarageId() {
        return correctedByGarageId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
