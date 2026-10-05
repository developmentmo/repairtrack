package com.repairtrack.audit.application;

import static com.repairtrack.audit.application.AuditTrail.values;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.repairtrack.audit.domain.AuditAction;
import com.repairtrack.audit.domain.AuditEntityType;
import com.repairtrack.dispute.DisputeEvents;
import com.repairtrack.document.DocumentEvents;
import com.repairtrack.garage.GarageEvents;
import com.repairtrack.repair.RepairEvents;
import com.repairtrack.security.UserAccountEvents;
import com.repairtrack.security.UserRegisteredEvent;
import com.repairtrack.sharing.SharingEvents;
import com.repairtrack.vehicle.VehicleEvents;

/**
 * Turns module events into audit entries.
 * <p>
 * Deliberately plain, synchronous {@code @EventListener}s: they run in the publisher's
 * transaction, so a mutation and its audit entry commit or roll back together. Do not switch
 * these to {@code @TransactionalEventListener}/async listeners; audit entries could then be lost.
 * When an outbox is introduced, it is written here in the same way.
 */
@Component
class AuditRecorder {

    private final AuditTrail trail;

    AuditRecorder(AuditTrail trail) {
        this.trail = trail;
    }

    // ---------- security ----------

    @EventListener
    void on(UserRegisteredEvent event) {
        trail.record(AuditEntityType.USER, event.userId(), AuditAction.USER_REGISTERED, event.userId(),
                null, null, event.occurredAt());
    }

    @EventListener
    void on(UserAccountEvents.EmailVerified event) {
        trail.record(AuditEntityType.USER, event.userId(), AuditAction.USER_EMAIL_VERIFIED, event.userId(),
                null, null, event.occurredAt());
    }

    @EventListener
    void on(UserAccountEvents.PasswordReset event) {
        trail.record(AuditEntityType.USER, event.userId(), AuditAction.USER_PASSWORD_RESET, event.userId(),
                null, null, event.occurredAt());
    }

    /** The account's own deletion; no personal data is recorded (the row itself is anonymized). */
    @EventListener
    void on(UserAccountEvents.AccountDeleted event) {
        trail.record(AuditEntityType.USER, event.userId(), AuditAction.USER_DELETED, event.userId(),
                null, null, event.occurredAt());
    }

    @EventListener
    void on(UserAccountEvents.UserBlocked event) {
        trail.record(AuditEntityType.USER, event.userId(), AuditAction.USER_BLOCKED, event.blockedBy(),
                null, null, event.occurredAt());
    }

    @EventListener
    void on(UserAccountEvents.UserUnblocked event) {
        trail.record(AuditEntityType.USER, event.userId(), AuditAction.USER_UNBLOCKED, event.unblockedBy(),
                null, null, event.occurredAt());
    }

    // ---------- garage ----------

    @EventListener
    void on(GarageEvents.GarageRegistered event) {
        trail.record(AuditEntityType.GARAGE, event.garageId(), AuditAction.GARAGE_REGISTERED, event.registeredBy(),
                null, null, event.occurredAt());
    }

    @EventListener
    void on(GarageEvents.GarageVerificationStatusChanged event) {
        AuditAction action = switch (event.to()) {
            case VERIFIED -> AuditAction.GARAGE_VERIFIED;
            case SUSPENDED -> AuditAction.GARAGE_SUSPENDED;
            case UNVERIFIED -> AuditAction.GARAGE_VERIFICATION_REVOKED;
            case PENDING -> AuditAction.GARAGE_VERIFICATION_REQUESTED;
        };
        trail.record(AuditEntityType.GARAGE, event.garageId(), action, event.changedBy(),
                values("verificationStatus", event.from()),
                values("verificationStatus", event.to(), "note", event.note()), event.occurredAt());
    }

    @EventListener
    void on(GarageEvents.GarageMemberAdded event) {
        trail.record(AuditEntityType.GARAGE, event.garageId(), AuditAction.GARAGE_MEMBER_ADDED, event.addedBy(),
                null, values("userId", event.userId(), "role", event.role()), event.occurredAt());
    }

    @EventListener
    void on(GarageEvents.GarageMemberRemoved event) {
        trail.record(AuditEntityType.GARAGE, event.garageId(), AuditAction.GARAGE_MEMBER_REMOVED, event.removedBy(),
                values("userId", event.userId()), null, event.occurredAt());
    }

    // ---------- vehicle ----------

    @EventListener
    void on(VehicleEvents.VehicleRegistered event) {
        trail.record(AuditEntityType.VEHICLE, event.vehicleId(), AuditAction.VEHICLE_REGISTERED, event.registeredBy(),
                null, values("garageId", event.garageId()), event.occurredAt());
    }

    @EventListener
    void on(VehicleEvents.VehicleDetailsChanged event) {
        Map<String, Object> oldValues = new LinkedHashMap<>();
        Map<String, Object> newValues = new LinkedHashMap<>();
        event.changes().forEach(change -> {
            oldValues.put(change.field(), change.oldValue());
            newValues.put(change.field(), change.newValue());
        });
        trail.record(AuditEntityType.VEHICLE, event.vehicleId(), AuditAction.VEHICLE_UPDATED, event.changedBy(),
                oldValues, newValues, event.occurredAt());
    }

    @EventListener
    void on(VehicleEvents.VehicleOwnershipStarted event) {
        AuditAction action = event.claimed() ? AuditAction.VEHICLE_CLAIMED : AuditAction.VEHICLE_OWNERSHIP_STARTED;
        trail.record(AuditEntityType.VEHICLE, event.vehicleId(), action, event.userId(),
                null, values("ownerId", event.userId(), "startDate", event.startDate()), event.occurredAt());
    }

    @EventListener
    void on(VehicleEvents.VehicleOwnershipEnded event) {
        trail.record(AuditEntityType.VEHICLE, event.vehicleId(), AuditAction.VEHICLE_OWNERSHIP_ENDED, event.userId(),
                values("ownerId", event.userId()), values("endDate", event.endDate()), event.occurredAt());
    }

    /** Upheld dispute: the actor is the system admin who decided. */
    @EventListener
    void on(VehicleEvents.VehicleOwnershipRevoked event) {
        trail.record(AuditEntityType.VEHICLE, event.vehicleId(), AuditAction.VEHICLE_OWNERSHIP_REVOKED,
                event.revokedBy(), values("ownerId", event.userId()),
                values("endDate", event.endDate(), "disputeId", event.disputeId()), event.occurredAt());
    }

    @EventListener
    void on(VehicleEvents.VehicleOwnershipAssigned event) {
        trail.record(AuditEntityType.VEHICLE, event.vehicleId(), AuditAction.VEHICLE_OWNERSHIP_ASSIGNED,
                event.assignedBy(), null,
                values("ownerId", event.userId(), "startDate", event.startDate(), "disputeId", event.disputeId()),
                event.occurredAt());
    }

    // ---------- dispute (never statements or file names) ----------

    @EventListener
    void on(DisputeEvents.DisputeOpened event) {
        trail.record(AuditEntityType.OWNERSHIP_DISPUTE, event.disputeId(), AuditAction.DISPUTE_OPENED,
                event.claimantId(), null,
                values("vehicleId", event.vehicleId(), "contestedOwnerId", event.ownerId()), event.occurredAt());
    }

    @EventListener
    void on(DisputeEvents.DisputeResponded event) {
        trail.record(AuditEntityType.OWNERSHIP_DISPUTE, event.disputeId(), AuditAction.DISPUTE_RESPONDED,
                event.ownerId(), null, values("vehicleId", event.vehicleId()), event.occurredAt());
    }

    @EventListener
    void on(DisputeEvents.DisputeEvidenceAdded event) {
        trail.record(AuditEntityType.OWNERSHIP_DISPUTE, event.disputeId(), AuditAction.DISPUTE_EVIDENCE_ADDED,
                event.submittedBy(), null,
                values("evidenceId", event.evidenceId(), "party", event.party(), "mimeType", event.mimeType(),
                        "fileSize", event.fileSize(), "sha256", event.sha256()),
                event.occurredAt());
    }

    /** Found by the system (no actor). */
    @EventListener
    void on(DisputeEvents.EvidenceIntegrityCheckFailed event) {
        trail.record(AuditEntityType.OWNERSHIP_DISPUTE, event.disputeId(),
                AuditAction.DISPUTE_EVIDENCE_INTEGRITY_FAILED, null, null,
                values("evidenceId", event.evidenceId(), "reason", event.reason()), event.occurredAt());
    }

    @EventListener
    void on(DisputeEvents.DisputeDecided event) {
        trail.record(AuditEntityType.OWNERSHIP_DISPUTE, event.disputeId(),
                event.upheld() ? AuditAction.DISPUTE_UPHELD : AuditAction.DISPUTE_REJECTED, event.decidedBy(),
                null, values("vehicleId", event.vehicleId(), "newOwnerSince", event.newOwnerSince()),
                event.occurredAt());
    }

    // ---------- repair ----------

    @EventListener
    void on(RepairEvents.RepairCreated event) {
        var s = event.snapshot();
        trail.record(AuditEntityType.REPAIR_EVENT, event.repairId(), AuditAction.REPAIR_CREATED, event.createdBy(),
                null, values("vehicleId", event.vehicleId(), "garageId", event.garageId(),
                        "eventType", s.eventType(), "eventDate", s.eventDate(), "mileage", s.mileage(),
                        "title", s.title(), "sourceType", s.sourceType(),
                        "verificationStatus", s.verificationStatus()),
                event.occurredAt());
    }

    @EventListener
    void on(RepairEvents.RepairCorrected event) {
        Map<String, Object> oldValues = new LinkedHashMap<>();
        Map<String, Object> newValues = new LinkedHashMap<>();
        event.corrections().forEach(c -> {
            oldValues.put(c.field(), c.oldValue());
            newValues.put(c.field(), c.newValue());
        });
        newValues.put("reason", event.reason());
        newValues.put("onBehalfOfGarageId", event.garageId());
        trail.record(AuditEntityType.REPAIR_EVENT, event.repairId(), AuditAction.REPAIR_CORRECTED,
                event.correctedBy(), oldValues, newValues, event.occurredAt());
    }

    @EventListener
    void on(RepairEvents.RepairVoided event) {
        trail.record(AuditEntityType.REPAIR_EVENT, event.repairId(), AuditAction.REPAIR_VOIDED, event.voidedBy(),
                values("status", "ACTIVE"), values("status", "VOIDED", "reason", event.reason()),
                event.occurredAt());
    }

    @EventListener
    void on(RepairEvents.RepairProvenanceChanged event) {
        trail.record(AuditEntityType.REPAIR_EVENT, event.repairId(), AuditAction.REPAIR_VERIFICATION_RAISED,
                event.changedBy(),
                values("sourceType", event.fromSource(), "verificationStatus", event.fromStatus()),
                values("sourceType", event.toSource(), "verificationStatus", event.toStatus(),
                        "evidenceDocumentId", event.evidenceDocumentId()),
                event.occurredAt());
    }

    // ---------- document ----------

    /** File names are deliberately not audited (they may contain personal data). */
    @EventListener
    void on(DocumentEvents.DocumentUploaded event) {
        trail.record(AuditEntityType.DOCUMENT, event.documentId(), AuditAction.DOCUMENT_UPLOADED, event.uploadedBy(),
                null, values("repairEventId", event.repairEventId(), "vehicleId", event.vehicleId(),
                        "documentType", event.documentType(), "mimeType", event.mimeType(),
                        "fileSize", event.fileSize(), "sha256", event.sha256()),
                event.occurredAt());
    }

    /** Rejected upload: recorded on the record the file was meant for. Only the signature, never file content. */
    @EventListener
    void on(DocumentEvents.UploadRejectedAsMalware event) {
        trail.record(AuditEntityType.REPAIR_EVENT, event.repairEventId(), AuditAction.DOCUMENT_MALWARE_REJECTED,
                event.uploadedBy(), null, values("signature", event.signature()), event.occurredAt());
    }

    /** Found by the system (no actor). */
    @EventListener
    void on(DocumentEvents.IntegrityCheckFailed event) {
        trail.record(AuditEntityType.DOCUMENT, event.documentId(), AuditAction.DOCUMENT_INTEGRITY_FAILED, null,
                null, values("reason", event.reason(), "repairEventId", event.repairEventId().toString()),
                event.occurredAt());
    }

    // ---------- sharing (never the token or its hash) ----------

    @EventListener
    void on(SharingEvents.ShareCreated event) {
        trail.record(AuditEntityType.VEHICLE_SHARE, event.shareId(), AuditAction.SHARE_CREATED, event.createdBy(),
                null, values("vehicleId", event.vehicleId(), "expiresAt", event.expiresAt(),
                        "includeDocuments", event.includeDocuments()),
                event.occurredAt());
    }

    @EventListener
    void on(SharingEvents.ShareRevoked event) {
        trail.record(AuditEntityType.VEHICLE_SHARE, event.shareId(), AuditAction.SHARE_REVOKED, event.revokedBy(),
                null, values("vehicleId", event.vehicleId()), event.occurredAt());
    }

    @EventListener
    void on(RepairEvents.RepairPartAdded event) {
        trail.record(AuditEntityType.REPAIR_EVENT, event.repairId(), AuditAction.REPAIR_PART_ADDED, event.addedBy(),
                null, values("partId", event.partId(), "partNumber", event.partNumber(), "brand", event.brand(),
                        "description", event.description(), "quantity", event.quantity()),
                event.occurredAt());
    }
}
