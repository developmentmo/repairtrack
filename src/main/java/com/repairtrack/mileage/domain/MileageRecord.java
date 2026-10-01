package com.repairtrack.mileage.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import com.repairtrack.mileage.MileageReading;
import com.repairtrack.verification.SourceType;

/**
 * An odometer reading. Never updated in place: when its source event is corrected or voided,
 * the record is VOIDED and (for a correction) a new record is created.
 */
@Entity
@Table(name = "mileage_record")
public class MileageRecord {

    @Id
    private UUID id;

    @Column(name = "vehicle_id", nullable = false, updatable = false)
    private UUID vehicleId;

    @Column(name = "mileage", nullable = false, updatable = false)
    private int mileage;

    @Column(name = "recorded_date", nullable = false, updatable = false)
    private LocalDate recordedDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, updatable = false, length = 20)
    private SourceType sourceType;

    @Column(name = "source_event_id", updatable = false)
    private UUID sourceEventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MileageRecordStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "voided_at")
    private Instant voidedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected MileageRecord() {
        // for JPA
    }

    public static MileageRecord record(UUID vehicleId, int mileage, LocalDate recordedDate, SourceType sourceType,
                                       UUID sourceEventId, Instant now) {
        if (mileage < 0) {
            throw new IllegalArgumentException("mileage must not be negative");
        }
        MileageRecord record = new MileageRecord();
        record.id = UUID.randomUUID();
        record.vehicleId = Objects.requireNonNull(vehicleId, "vehicleId");
        record.mileage = mileage;
        record.recordedDate = Objects.requireNonNull(recordedDate, "recordedDate");
        record.sourceType = Objects.requireNonNull(sourceType, "sourceType");
        record.sourceEventId = sourceEventId;
        record.status = MileageRecordStatus.ACTIVE;
        record.createdAt = Objects.requireNonNull(now, "now");
        return record;
    }

    public void voidRecord(Instant now) {
        if (status == MileageRecordStatus.ACTIVE) {
            status = MileageRecordStatus.VOIDED;
            voidedAt = now;
        }
    }

    public MileageReading toReading() {
        return new MileageReading(id, mileage, recordedDate, sourceType, sourceEventId);
    }

    public UUID getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public LocalDate getRecordedDate() {
        return recordedDate;
    }

    public MileageRecordStatus getStatus() {
        return status;
    }
}
