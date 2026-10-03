package com.repairtrack.dispute.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.repairtrack.dispute.domain.DisputeEvidence;
import com.repairtrack.dispute.domain.DisputeParty;
import com.repairtrack.dispute.domain.DisputeStatus;
import com.repairtrack.vehicle.VehicleSummary;

public final class DisputeViews {

    private DisputeViews() {
    }

    public record VehicleInfo(UUID id, String licensePlate, String make, String model) {

        static VehicleInfo of(VehicleSummary summary) {
            return summary == null ? null
                    : new VehicleInfo(summary.id(), summary.licensePlate(), summary.make(), summary.model());
        }
    }

    public record EvidenceView(UUID id, DisputeParty party, String fileName, String mimeType, long fileSize,
                               String sha256, Instant uploadedAt) {

        static EvidenceView of(DisputeEvidence e) {
            return new EvidenceView(e.getId(), e.getParty(), e.getFileName(), e.getMimeType(), e.getFileSize(),
                    e.getSha256(), e.getUploadedAt());
        }
    }

    /**
     * A dispute as one of its parties sees it: their own statement and files only, never the other party's
     * identity, statement or files.
     */
    public record PartyDisputeView(
            UUID id,
            DisputeParty role,
            VehicleInfo vehicle,
            DisputeStatus status,
            Instant createdAt,
            Instant responseDeadline,
            boolean canRespond,
            boolean canAddEvidence,
            String myStatement,
            List<EvidenceView> myEvidence,
            Instant decidedAt,
            String decisionNote,
            LocalDate newOwnerSince
    ) {
    }

    public record PartyInfo(UUID id, String email, String firstName, String lastName) {
    }

    /** Everything, for system admins. */
    public record AdminDisputeView(
            UUID id,
            VehicleInfo vehicle,
            DisputeStatus status,
            boolean reviewable,
            PartyInfo claimant,
            PartyInfo owner,
            String claimantStatement,
            String ownerStatement,
            Instant ownerRespondedAt,
            Instant createdAt,
            Instant responseDeadline,
            List<EvidenceView> evidence,
            UUID decidedBy,
            Instant decidedAt,
            String decisionNote,
            LocalDate newOwnerSince
    ) {
    }

    public record EvidenceDownload(EvidenceView evidence, String downloadUrl, Instant expiresAt) {
    }
}
