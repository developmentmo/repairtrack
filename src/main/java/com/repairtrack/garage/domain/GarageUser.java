package com.repairtrack.garage.domain;

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

import com.repairtrack.garage.GarageRole;

/**
 * Membership of a user in a garage, with a garage-scoped role.
 * <p>
 * Never deleted: ending a membership sets status ENDED so work recorded by a former
 * mechanic stays attributable. Re-joining creates a new membership row.
 */
@Entity
@Table(name = "garage_user")
public class GarageUser {

    @Id
    private UUID id;

    @Column(name = "garage_id", nullable = false, updatable = false)
    private UUID garageId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private GarageRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MembershipStatus status;

    @Column(name = "added_by", nullable = false, updatable = false)
    private UUID addedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "ended_by")
    private UUID endedBy;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected GarageUser() {
        // for JPA
    }

    public static GarageUser add(UUID garageId, UUID userId, GarageRole role, UUID addedBy, Instant now) {
        GarageUser membership = new GarageUser();
        membership.id = UUID.randomUUID();
        membership.garageId = Objects.requireNonNull(garageId, "garageId");
        membership.userId = Objects.requireNonNull(userId, "userId");
        membership.role = Objects.requireNonNull(role, "role");
        membership.status = MembershipStatus.ACTIVE;
        membership.addedBy = Objects.requireNonNull(addedBy, "addedBy");
        membership.createdAt = Objects.requireNonNull(now, "now");
        return membership;
    }

    public void end(UUID endedBy, Instant now) {
        if (status == MembershipStatus.ENDED) {
            throw new IllegalStateException("Membership already ended");
        }
        this.status = MembershipStatus.ENDED;
        this.endedBy = Objects.requireNonNull(endedBy, "endedBy");
        this.endedAt = Objects.requireNonNull(now, "now");
    }

    public boolean isActive() {
        return status == MembershipStatus.ACTIVE;
    }

    public boolean isAdmin() {
        return role == GarageRole.GARAGE_ADMIN;
    }

    public UUID getId() {
        return id;
    }

    public UUID getGarageId() {
        return garageId;
    }

    public UUID getUserId() {
        return userId;
    }

    public GarageRole getRole() {
        return role;
    }

    public MembershipStatus getStatus() {
        return status;
    }

    public UUID getAddedBy() {
        return addedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }
}
