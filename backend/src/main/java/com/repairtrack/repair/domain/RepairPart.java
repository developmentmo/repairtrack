package com.repairtrack.repair.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import org.springframework.data.domain.Persistable;

/** A part used in a repair. Optional; a repair can have none. Append-only in V1. */
@Entity
@Table(name = "repair_part")
public class RepairPart implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "repair_event_id", nullable = false, updatable = false)
    private UUID repairEventId;

    @Column(name = "part_number", updatable = false, length = 100)
    private String partNumber;

    @Column(name = "brand", updatable = false, length = 100)
    private String brand;

    @Column(name = "description", nullable = false, updatable = false, length = 500)
    private String description;

    @Column(name = "quantity", nullable = false, updatable = false)
    private int quantity;

    @Column(name = "added_by", nullable = false, updatable = false)
    private UUID addedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Immutable rows have no @Version; this tells Spring Data to INSERT instead of merging. */
    @Transient
    private boolean isNew = true;

    protected RepairPart() {
        // for JPA
    }

    public static RepairPart add(UUID repairEventId, String partNumber, String brand, String description,
                                 int quantity, UUID addedBy, Instant now) {
        if (description == null || description.isBlank()) {
            throw new InvalidRepairDataException("Part description is required.");
        }
        if (quantity < 1 || quantity > 999) {
            throw new InvalidRepairDataException("Part quantity must be between 1 and 999.");
        }
        RepairPart part = new RepairPart();
        part.id = UUID.randomUUID();
        part.repairEventId = Objects.requireNonNull(repairEventId, "repairEventId");
        part.partNumber = blankToNull(partNumber);
        part.brand = blankToNull(brand);
        part.description = description.trim();
        part.quantity = quantity;
        part.addedBy = Objects.requireNonNull(addedBy, "addedBy");
        part.createdAt = Objects.requireNonNull(now, "now");
        return part;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
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

    public String getPartNumber() {
        return partNumber;
    }

    public String getBrand() {
        return brand;
    }

    public String getDescription() {
        return description;
    }

    public int getQuantity() {
        return quantity;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
