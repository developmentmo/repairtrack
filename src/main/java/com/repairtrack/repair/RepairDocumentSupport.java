package com.repairtrack.repair;

import java.time.Instant;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.repairtrack.common.time.BusinessCalendar;
import com.repairtrack.repair.application.RepairAccessPolicy;
import com.repairtrack.repair.application.RepairNotFoundException;
import com.repairtrack.repair.domain.RepairAlreadyVoidedException;
import com.repairtrack.repair.domain.RepairEvent;
import com.repairtrack.repair.infrastructure.RepairEventRepository;
import com.repairtrack.security.AuthenticatedUser;
import com.repairtrack.verification.Provenance;
import com.repairtrack.verification.RecordingContext;
import com.repairtrack.verification.SourceType;
import com.repairtrack.verification.VerificationLog;
import com.repairtrack.verification.VerificationMethod;
import com.repairtrack.verification.VerificationService;

/**
 * Repair module API for the document module: authorization for attaching and viewing documents,
 * and the provenance upgrade an attached document can cause. Keeps the dependency one-way
 * (document -> repair) and all repair rules inside the repair module.
 */
@Service
public class RepairDocumentSupport {

    private final RepairEventRepository repairs;
    private final RepairAccessPolicy access;
    private final VerificationService verification;
    private final VerificationLog verificationLog;
    private final ApplicationEventPublisher events;
    private final BusinessCalendar calendar;

    public RepairDocumentSupport(RepairEventRepository repairs, RepairAccessPolicy access,
                                 VerificationService verification, VerificationLog verificationLog,
                                 ApplicationEventPublisher events, BusinessCalendar calendar) {
        this.repairs = repairs;
        this.access = access;
        this.verification = verification;
        this.verificationLog = verificationLog;
        this.events = events;
        this.calendar = calendar;
    }

    /** What a document is being attached to. */
    public record AttachmentTarget(UUID repairId, UUID vehicleId, SourceType sourceType) {
    }

    /** Same rights as correcting the record; voided records accept no new documents. */
    @Transactional(readOnly = true)
    public AttachmentTarget requireCanAttachDocument(AuthenticatedUser actor, UUID repairId) {
        RepairEvent event = repairs.findById(repairId).orElseThrow(RepairNotFoundException::new);
        access.requireCanModify(actor, event);
        if (event.isVoided()) {
            throw new RepairAlreadyVoidedException();
        }
        return new AttachmentTarget(event.getId(), event.getVehicleId(), event.getSourceType());
    }

    /** Documents are visible to whoever may see the vehicle's history. Returns the vehicle ID. */
    @Transactional(readOnly = true)
    public UUID requireCanViewRepair(AuthenticatedUser actor, UUID repairId) {
        RepairEvent event = repairs.findById(repairId).orElseThrow(RepairNotFoundException::new);
        access.requireCanViewHistory(actor, event.getVehicleId());
        return event.getVehicleId();
    }

    /**
     * Called after an evidentiary document (invoice, work order, inspection report) has been stored
     * for the record. An owner record becomes OWNER_DOCUMENT / DOCUMENTED; other records are unchanged.
     *
     * @return true if the provenance was raised
     */
    @Transactional
    public boolean applyDocumentEvidence(AuthenticatedUser actor, UUID repairId, UUID documentId) {
        RepairEvent event = repairs.findById(repairId).orElseThrow(RepairNotFoundException::new);
        Provenance documented = verification.determine(new RecordingContext.ByOwnerWithDocument());
        Instant now = calendar.now();
        Provenance previous = event.applyDocumentEvidence(documented, now);
        if (previous == null) {
            return false;
        }
        verificationLog.record(repairId, previous, documented, VerificationMethod.DOCUMENT_ATTACHED, documentId,
                actor.id(), now);
        events.publishEvent(new RepairEvents.RepairProvenanceChanged(repairId, event.getVehicleId(),
                previous.sourceType(), previous.verificationStatus(), documented.sourceType(),
                documented.verificationStatus(), documentId, actor.id(), now));
        return true;
    }
}
