package com.repairtrack.sharing.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * A revocable, expiring link to a vehicle's public history. Only the SHA-256 hash of the token is
 * stored; the token itself is shown once, at creation. The share's ID is internal and never part of
 * the public URL.
 */
@Entity
@Table(name = "vehicle_share")
public class VehicleShare {

    public static final Duration MIN_VALIDITY = Duration.ofDays(1);
    public static final Duration MAX_VALIDITY = Duration.ofDays(365);

    @Id
    private UUID id;

    @Column(name = "vehicle_id", nullable = false, updatable = false)
    private UUID vehicleId;

    @Column(name = "token_hash", nullable = false, updatable = false, length = 64)
    private String tokenHash;

    /** Whether buyers may download the documents (invoices, ...) of the history. Off by default. */
    @Column(name = "include_documents", nullable = false, updatable = false)
    private boolean includeDocuments;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_by")
    private UUID revokedBy;

    /** Maintained with an atomic UPDATE (see repository), never through this entity. */
    @Column(name = "access_count", nullable = false, insertable = false, updatable = false)
    private long accessCount;

    @Column(name = "last_accessed_at", insertable = false, updatable = false)
    private Instant lastAccessedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected VehicleShare() {
        // for JPA
    }

    public static VehicleShare create(UUID vehicleId, String tokenHash, boolean includeDocuments, Duration validity,
                                      UUID createdBy, Instant now) {
        if (validity.compareTo(MIN_VALIDITY) < 0 || validity.compareTo(MAX_VALIDITY) > 0) {
            throw new InvalidShareException("A share must be valid for 1 to 365 days.");
        }
        VehicleShare share = new VehicleShare();
        share.id = UUID.randomUUID();
        share.vehicleId = Objects.requireNonNull(vehicleId, "vehicleId");
        share.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash");
        share.includeDocuments = includeDocuments;
        share.expiresAt = now.plus(validity);
        share.createdBy = Objects.requireNonNull(createdBy, "createdBy");
        share.createdAt = Objects.requireNonNull(now, "now");
        return share;
    }

    /**
     * @param creatorIsCurrentOwner whether {@link #createdBy} still owns the vehicle. A link must not
     *                              keep showing a vehicle's history after it was sold: that would
     *                              expose the next owner's records to whoever got the old link.
     */
    public ShareStatus status(Instant now, boolean creatorIsCurrentOwner) {
        if (revokedAt != null) {
            return ShareStatus.REVOKED;
        }
        if (!now.isBefore(expiresAt)) {
            return ShareStatus.EXPIRED;
        }
        if (!creatorIsCurrentOwner) {
            return ShareStatus.OWNER_CHANGED;
        }
        return ShareStatus.ACTIVE;
    }

    /** Idempotent. */
    public boolean revoke(UUID by, Instant now) {
        if (revokedAt != null) {
            return false;
        }
        revokedAt = now;
        revokedBy = Objects.requireNonNull(by, "by");
        return true;
    }

    public UUID getId() {
        return id;
    }

    public UUID getVehicleId() {
        return vehicleId;
    }

    public boolean isIncludeDocuments() {
        return includeDocuments;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public long getAccessCount() {
        return accessCount;
    }

    public Instant getLastAccessedAt() {
        return lastAccessedAt;
    }
}
