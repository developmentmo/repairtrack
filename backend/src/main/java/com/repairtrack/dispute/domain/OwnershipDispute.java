package com.repairtrack.dispute.domain;

import java.time.Duration;
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
 * A claim that the current owner of a vehicle is not its rightful owner.
 * <p>
 * OPEN (owner may respond until {@code responseDeadline}) &rarr; AWAITING_REVIEW (responded or deadline passed)
 * &rarr; UPHELD | REJECTED (system admin). A decision is final; a new dispute can be filed afterwards.
 */
@Entity
@Table(name = "ownership_dispute")
public class OwnershipDispute {

    public static final int MIN_STATEMENT = 10;
    public static final int MAX_STATEMENT = 2000;

    @Id
    private UUID id;

    @Column(name = "vehicle_id", nullable = false, updatable = false)
    private UUID vehicleId;

    @Column(name = "claimant_id", nullable = false, updatable = false)
    private UUID claimantId;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(name = "contested_ownership_id", nullable = false, updatable = false)
    private UUID contestedOwnershipId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DisputeStatus status;

    @Column(name = "claimant_statement", nullable = false, updatable = false, length = MAX_STATEMENT)
    private String claimantStatement;

    @Column(name = "owner_statement", length = MAX_STATEMENT)
    private String ownerStatement;

    @Column(name = "response_deadline", nullable = false, updatable = false)
    private Instant responseDeadline;

    @Column(name = "owner_responded_at")
    private Instant ownerRespondedAt;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_note", length = MAX_STATEMENT)
    private String decisionNote;

    @Column(name = "new_owner_since")
    private LocalDate newOwnerSince;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected OwnershipDispute() {
        // for JPA
    }

    public static OwnershipDispute open(UUID vehicleId, UUID claimantId, UUID ownerId, UUID contestedOwnershipId,
                                        String statement, Duration responseTime, Instant now) {
        OwnershipDispute dispute = new OwnershipDispute();
        dispute.id = UUID.randomUUID();
        dispute.vehicleId = Objects.requireNonNull(vehicleId, "vehicleId");
        dispute.claimantId = Objects.requireNonNull(claimantId, "claimantId");
        dispute.ownerId = Objects.requireNonNull(ownerId, "ownerId");
        if (claimantId.equals(ownerId)) {
            throw new IllegalArgumentException("The owner cannot dispute their own ownership");
        }
        dispute.contestedOwnershipId = Objects.requireNonNull(contestedOwnershipId, "contestedOwnershipId");
        dispute.claimantStatement = statement(statement);
        dispute.status = DisputeStatus.OPEN;
        dispute.createdAt = Objects.requireNonNull(now, "now");
        dispute.responseDeadline = now.plus(responseTime);
        return dispute;
    }

    /** The contested owner's side. Only once, and only before the deadline. */
    public void respond(String statement, Instant now) {
        if (!canRespond(now)) {
            throw new DisputeClosedException("The response period of this dispute is over.");
        }
        this.ownerStatement = statement(statement);
        this.ownerRespondedAt = now;
        this.status = DisputeStatus.AWAITING_REVIEW;
    }

    public boolean canRespond(Instant now) {
        return status == DisputeStatus.OPEN && now.isBefore(responseDeadline);
    }

    /** A system admin can decide once the owner responded or the deadline passed. */
    public boolean isReviewable(Instant now) {
        return status == DisputeStatus.AWAITING_REVIEW || (status == DisputeStatus.OPEN && !now.isBefore(responseDeadline));
    }

    public boolean isUndecided() {
        return !status.isDecided();
    }

    public void uphold(UUID decidedBy, String note, LocalDate newOwnerSince, Instant now) {
        decide(DisputeStatus.UPHELD, decidedBy, note, now);
        this.newOwnerSince = Objects.requireNonNull(newOwnerSince, "newOwnerSince");
    }

    public void reject(UUID decidedBy, String note, Instant now) {
        decide(DisputeStatus.REJECTED, decidedBy, note, now);
    }

    private void decide(DisputeStatus outcome, UUID decidedBy, String note, Instant now) {
        if (status.isDecided()) {
            throw new DisputeClosedException("This dispute has already been decided.");
        }
        if (!isReviewable(now)) {
            throw new DisputeNotReviewableException();
        }
        this.decisionNote = statement(note);
        this.status = outcome;
        this.decidedBy = Objects.requireNonNull(decidedBy, "decidedBy");
        this.decidedAt = now;
    }

    private static String statement(String text) {
        String trimmed = text == null ? "" : text.strip();
        if (trimmed.length() < MIN_STATEMENT || trimmed.length() > MAX_STATEMENT) {
            throw new InvalidDisputeException(
                    "A statement needs " + MIN_STATEMENT + " to " + MAX_STATEMENT + " characters.");
        }
        return trimmed;
    }

    public UUID getId() {
        return id;
    }

    public UUID getVehicleId() {
        return vehicleId;
    }

    public UUID getClaimantId() {
        return claimantId;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public UUID getContestedOwnershipId() {
        return contestedOwnershipId;
    }

    public DisputeStatus getStatus() {
        return status;
    }

    public String getClaimantStatement() {
        return claimantStatement;
    }

    public String getOwnerStatement() {
        return ownerStatement;
    }

    public Instant getResponseDeadline() {
        return responseDeadline;
    }

    public Instant getOwnerRespondedAt() {
        return ownerRespondedAt;
    }

    public UUID getDecidedBy() {
        return decidedBy;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public String getDecisionNote() {
        return decisionNote;
    }

    public LocalDate getNewOwnerSince() {
        return newOwnerSince;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
