package com.repairtrack.vehicle.domain;

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

/**
 * A period during which a user owned a vehicle. A vehicle has at most one ACTIVE ownership;
 * ended ownerships are kept, so the number and duration of previous ownerships remains known
 * without ever exposing who the previous owners were.
 */
@Entity
@Table(name = "vehicle_ownership")
public class VehicleOwnership {

    @Id
    private UUID id;

    @Column(name = "vehicle_id", nullable = false, updatable = false)
    private UUID vehicleId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "start_date", nullable = false, updatable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OwnershipStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected VehicleOwnership() {
        // for JPA
    }

    public static VehicleOwnership start(UUID vehicleId, UUID userId, LocalDate startDate, Instant now,
                                         LocalDate today) {
        Objects.requireNonNull(startDate, "startDate");
        if (startDate.isAfter(today)) {
            throw new InvalidOwnershipPeriodException("Ownership cannot start in the future.");
        }
        VehicleOwnership ownership = new VehicleOwnership();
        ownership.id = UUID.randomUUID();
        ownership.vehicleId = Objects.requireNonNull(vehicleId, "vehicleId");
        ownership.userId = Objects.requireNonNull(userId, "userId");
        ownership.startDate = startDate;
        ownership.status = OwnershipStatus.ACTIVE;
        ownership.createdAt = Objects.requireNonNull(now, "now");
        return ownership;
    }

    public void end(LocalDate endDate, Instant now, LocalDate today) {
        Objects.requireNonNull(endDate, "endDate");
        if (status == OwnershipStatus.ENDED) {
            throw new IllegalStateException("Ownership already ended");
        }
        if (endDate.isBefore(startDate)) {
            throw new InvalidOwnershipPeriodException("Ownership cannot end before it started (" + startDate + ").");
        }
        if (endDate.isAfter(today)) {
            throw new InvalidOwnershipPeriodException("Ownership cannot end in the future.");
        }
        this.endDate = endDate;
        this.status = OwnershipStatus.ENDED;
        this.endedAt = Objects.requireNonNull(now, "now");
    }

    public boolean isActive() {
        return status == OwnershipStatus.ACTIVE;
    }

    public UUID getId() {
        return id;
    }

    public UUID getVehicleId() {
        return vehicleId;
    }

    public UUID getUserId() {
        return userId;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public OwnershipStatus getStatus() {
        return status;
    }
}
