package com.repairtrack.repair.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import com.repairtrack.repair.RepairEventType;
import com.repairtrack.repair.RepairEvents.RepairSnapshot;
import com.repairtrack.verification.Provenance;
import com.repairtrack.verification.SourceType;
import com.repairtrack.verification.VerificationStatus;

/**
 * One entry in a vehicle's history: maintenance, repair, inspection, ...
 * <p>
 * History is never silently rewritten:
 * <ul>
 *   <li>no deletion: a wrong record is {@linkplain #voidEvent voided} with a reason and stays visible;</li>
 *   <li>no plain update: {@linkplain #correct corrections} produce {@link RepairCorrection} rows that keep
 *       the original value, the new value, the reason, who and when;</li>
 *   <li>source type and verification status are set once from a {@link Provenance} decided by the
 *       backend and are never changed by corrections.</li>
 * </ul>
 */
@Entity
@Table(name = "repair_event")
public class RepairEvent {

    public static final int MAX_MILEAGE = 2_000_000;
    private static final int TITLE_MAX = 150;
    private static final int DESCRIPTION_MAX = 5000;

    @Id
    private UUID id;

    @Column(name = "vehicle_id", nullable = false, updatable = false)
    private UUID vehicleId;

    @Column(name = "garage_id", updatable = false)
    private UUID garageId;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 20)
    private RepairEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, updatable = false, length = 20)
    private SourceType sourceType;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 20)
    private VerificationStatus verificationStatus;

    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate;

    @Column(name = "mileage", nullable = false)
    private int mileage;

    @Column(name = "title", nullable = false, length = TITLE_MAX)
    private String title;

    @Column(name = "description", length = DESCRIPTION_MAX)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RepairStatus status;

    @Column(name = "voided_at")
    private Instant voidedAt;

    @Column(name = "voided_by")
    private UUID voidedBy;

    @Column(name = "void_reason", length = 500)
    private String voidReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected RepairEvent() {
        // for JPA
    }

    /**
     * @param garageId   the garage the work was done by; required for garage sources, must be null otherwise
     * @param provenance decided by the verification module; never from client input
     */
    public static RepairEvent record(UUID vehicleId, UUID garageId, UUID createdBy, RepairEventType eventType,
                                     Provenance provenance, LocalDate eventDate, int mileage, String title,
                                     String description, Instant now, LocalDate today) {
        boolean garageSource = provenance.sourceType() == SourceType.GARAGE
                || provenance.sourceType() == SourceType.VERIFIED_GARAGE;
        if (garageSource != (garageId != null)) {
            throw new IllegalArgumentException("garageId must be set exactly for garage sources");
        }
        RepairEvent event = new RepairEvent();
        event.id = UUID.randomUUID();
        event.vehicleId = Objects.requireNonNull(vehicleId, "vehicleId");
        event.garageId = garageId;
        event.createdBy = Objects.requireNonNull(createdBy, "createdBy");
        event.eventType = Objects.requireNonNull(eventType, "eventType");
        event.sourceType = provenance.sourceType();
        event.verificationStatus = provenance.verificationStatus();
        event.eventDate = validEventDate(eventDate, today);
        event.mileage = validMileage(mileage);
        event.title = validTitle(title);
        event.description = validDescription(description);
        event.status = RepairStatus.ACTIVE;
        event.createdAt = Objects.requireNonNull(now, "now");
        event.updatedAt = now;
        return event;
    }

    /**
     * Applies a correction and returns one {@link RepairCorrection} per changed field.
     *
     * @throws RepairAlreadyVoidedException if the record was voided
     * @throws NoChangesException           if nothing would change
     */
    public List<RepairCorrection> correct(RepairChanges changes, String reason, UUID correctedBy,
                                          UUID correctedByGarageId, Instant now, LocalDate today) {
        requireActive();
        String why = requireReason(reason);
        List<RepairCorrection> corrections = new ArrayList<>();

        if (changes.eventType() != null && changes.eventType() != eventType) {
            corrections.add(RepairCorrection.of(id, CorrectableField.EVENT_TYPE, eventType, changes.eventType(),
                    why, correctedBy, correctedByGarageId, now));
            eventType = changes.eventType();
        }
        if (changes.eventDate() != null && !changes.eventDate().equals(eventDate)) {
            LocalDate newDate = validEventDate(changes.eventDate(), today);
            corrections.add(RepairCorrection.of(id, CorrectableField.EVENT_DATE, eventDate, newDate,
                    why, correctedBy, correctedByGarageId, now));
            eventDate = newDate;
        }
        if (changes.mileage() != null && changes.mileage() != mileage) {
            int newMileage = validMileage(changes.mileage());
            corrections.add(RepairCorrection.of(id, CorrectableField.MILEAGE, mileage, newMileage,
                    why, correctedBy, correctedByGarageId, now));
            mileage = newMileage;
        }
        if (changes.title() != null && !changes.title().trim().equals(title)) {
            String newTitle = validTitle(changes.title());
            corrections.add(RepairCorrection.of(id, CorrectableField.TITLE, title, newTitle,
                    why, correctedBy, correctedByGarageId, now));
            title = newTitle;
        }
        if (changes.description() != null && !Objects.equals(validDescription(changes.description()), description)) {
            String newDescription = validDescription(changes.description());
            corrections.add(RepairCorrection.of(id, CorrectableField.DESCRIPTION, description, newDescription,
                    why, correctedBy, correctedByGarageId, now));
            description = newDescription;
        }

        if (corrections.isEmpty()) {
            throw new NoChangesException();
        }
        updatedAt = now;
        return List.copyOf(corrections);
    }

    /** Marks the record as invalid. It remains in the history, flagged, with the reason. */
    public void voidEvent(String reason, UUID voidedBy, Instant now) {
        requireActive();
        this.voidReason = requireReason(reason);
        this.voidedBy = Objects.requireNonNull(voidedBy, "voidedBy");
        this.voidedAt = Objects.requireNonNull(now, "now");
        this.status = RepairStatus.VOIDED;
        this.updatedAt = now;
    }

    public boolean isVoided() {
        return status == RepairStatus.VOIDED;
    }

    public boolean isGarageRecord() {
        return garageId != null;
    }

    public RepairSnapshot snapshot() {
        return new RepairSnapshot(eventType, eventDate, mileage, title, sourceType, verificationStatus);
    }

    private void requireActive() {
        if (isVoided()) {
            throw new RepairAlreadyVoidedException();
        }
    }

    private static String requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new InvalidRepairDataException("A reason is required.");
        }
        if (reason.length() > 500) {
            throw new InvalidRepairDataException("The reason must not exceed 500 characters.");
        }
        return reason.trim();
    }

    private static LocalDate validEventDate(LocalDate eventDate, LocalDate today) {
        Objects.requireNonNull(eventDate, "eventDate");
        if (eventDate.isAfter(today)) {
            throw new InvalidRepairDataException("The event date cannot be in the future.");
        }
        if (eventDate.getYear() < 1886) {
            throw new InvalidRepairDataException("The event date is not plausible.");
        }
        return eventDate;
    }

    private static int validMileage(int mileage) {
        if (mileage < 0 || mileage > MAX_MILEAGE) {
            throw new InvalidRepairDataException("Mileage must be between 0 and " + MAX_MILEAGE + " km.");
        }
        return mileage;
    }

    private static String validTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new InvalidRepairDataException("A title is required.");
        }
        String trimmed = title.trim();
        if (trimmed.length() > TITLE_MAX) {
            throw new InvalidRepairDataException("The title must not exceed " + TITLE_MAX + " characters.");
        }
        return trimmed;
    }

    private static String validDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        String trimmed = description.trim();
        if (trimmed.length() > DESCRIPTION_MAX) {
            throw new InvalidRepairDataException(
                    "The description must not exceed " + DESCRIPTION_MAX + " characters.");
        }
        return trimmed;
    }

    public UUID getId() {
        return id;
    }

    public UUID getVehicleId() {
        return vehicleId;
    }

    public UUID getGarageId() {
        return garageId;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public RepairEventType getEventType() {
        return eventType;
    }

    public SourceType getSourceType() {
        return sourceType;
    }

    public VerificationStatus getVerificationStatus() {
        return verificationStatus;
    }

    public LocalDate getEventDate() {
        return eventDate;
    }

    public int getMileage() {
        return mileage;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public RepairStatus getStatus() {
        return status;
    }

    public Instant getVoidedAt() {
        return voidedAt;
    }

    public String getVoidReason() {
        return voidReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
