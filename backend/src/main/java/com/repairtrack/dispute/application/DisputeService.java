package com.repairtrack.dispute.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.repairtrack.common.time.BusinessCalendar;
import com.repairtrack.dispute.DisputeEvents;
import com.repairtrack.dispute.application.DisputeExceptions.AlreadyOwnerException;
import com.repairtrack.dispute.application.DisputeExceptions.DisputeAccessDeniedException;
import com.repairtrack.dispute.application.DisputeExceptions.DisputeAlreadyOpenException;
import com.repairtrack.dispute.application.DisputeExceptions.DisputeNotFoundException;
import com.repairtrack.dispute.application.DisputeExceptions.TooManyOpenDisputesException;
import com.repairtrack.dispute.application.DisputeExceptions.TooMuchEvidenceException;
import com.repairtrack.dispute.application.DisputeExceptions.VehicleNotOwnedException;
import com.repairtrack.dispute.application.DisputeViews.EvidenceView;
import com.repairtrack.dispute.application.DisputeViews.PartyDisputeView;
import com.repairtrack.dispute.application.DisputeViews.VehicleInfo;
import com.repairtrack.dispute.domain.DisputeClosedException;
import com.repairtrack.dispute.domain.DisputeEvidence;
import com.repairtrack.dispute.domain.DisputeParty;
import com.repairtrack.dispute.domain.DisputeStatus;
import com.repairtrack.dispute.domain.InvalidDisputeException;
import com.repairtrack.dispute.domain.OwnershipDispute;
import com.repairtrack.dispute.infrastructure.DisputeEvidenceRepository;
import com.repairtrack.dispute.infrastructure.DisputeProperties;
import com.repairtrack.dispute.infrastructure.OwnershipDisputeRepository;
import com.repairtrack.document.EvidenceFiles;
import com.repairtrack.document.EvidenceFiles.StoredEvidence;
import com.repairtrack.security.AuthenticatedUser;
import com.repairtrack.vehicle.CurrentOwnership;
import com.repairtrack.vehicle.OwnershipDisputeSupport;
import com.repairtrack.vehicle.VehicleDirectory;
import com.repairtrack.vehicle.VehicleSummary;

/**
 * The parties' side of a dispute: the claimant files it (full VIN + statement + at least one file), the contested
 * owner responds once before the deadline, both may add files until it is decided. Non-parties get 404.
 */
@Service
public class DisputeService {

    private final OwnershipDisputeRepository disputes;
    private final DisputeEvidenceRepository evidence;
    private final OwnershipDisputeSupport ownership;
    private final VehicleDirectory vehicleDirectory;
    private final EvidenceFiles files;
    private final DisputeProperties properties;
    private final DisputeMails mails;
    private final ApplicationEventPublisher events;
    private final BusinessCalendar calendar;

    public DisputeService(OwnershipDisputeRepository disputes, DisputeEvidenceRepository evidence,
                          OwnershipDisputeSupport ownership, VehicleDirectory vehicleDirectory, EvidenceFiles files,
                          DisputeProperties properties, DisputeMails mails, ApplicationEventPublisher events,
                          BusinessCalendar calendar) {
        this.disputes = disputes;
        this.evidence = evidence;
        this.ownership = ownership;
        this.vehicleDirectory = vehicleDirectory;
        this.files = files;
        this.properties = properties;
        this.mails = mails;
        this.events = events;
        this.calendar = calendar;
    }

    @Transactional
    public PartyDisputeView open(AuthenticatedUser actor, UUID vehicleId, String vin, String statement,
                                 EvidenceUpload file) {
        ownership.requireVinMatches(vehicleId, vin);
        CurrentOwnership current = ownership.currentOwnership(vehicleId).orElseThrow(VehicleNotOwnedException::new);
        if (current.ownerId().equals(actor.id())) {
            throw new AlreadyOwnerException();
        }
        if (disputes.existsByVehicleIdAndClaimantIdAndStatusIn(vehicleId, actor.id(), DisputeStatus.UNDECIDED)) {
            throw new DisputeAlreadyOpenException();
        }
        if (disputes.countByClaimantIdAndStatusIn(actor.id(), DisputeStatus.UNDECIDED)
                >= properties.maxOpenPerClaimant()) {
            throw new TooManyOpenDisputesException();
        }
        if (file == null) {
            throw new InvalidDisputeException("Add at least one file that shows you are the owner.");
        }

        Instant now = calendar.now();
        OwnershipDispute dispute = OwnershipDispute.open(vehicleId, actor.id(), current.ownerId(),
                current.ownershipId(), statement, properties.responseTime(), now);
        try {
            disputes.saveAndFlush(dispute);
        } catch (DataIntegrityViolationException ex) {
            throw new DisputeAlreadyOpenException(); // partial unique index: one open dispute per claimant
        }
        events.publishEvent(new DisputeEvents.DisputeOpened(dispute.getId(), vehicleId, actor.id(),
                current.ownerId(), now));
        store(dispute, DisputeParty.CLAIMANT, actor, file, now);
        VehicleSummary vehicle = vehicle(vehicleId);
        mails.opened(dispute, vehicle);
        return view(dispute, DisputeParty.CLAIMANT, vehicle, now);
    }

    /** The contested owner's side: a statement and optionally a file. */
    @Transactional
    public PartyDisputeView respond(AuthenticatedUser actor, UUID disputeId, String statement, EvidenceUpload file) {
        OwnershipDispute dispute = asParty(actor, disputeId);
        if (!dispute.getOwnerId().equals(actor.id())) {
            throw new DisputeAccessDeniedException("Only the contested owner can respond.");
        }
        Instant now = calendar.now();
        dispute.respond(statement, now);
        disputes.save(dispute);
        events.publishEvent(new DisputeEvents.DisputeResponded(dispute.getId(), dispute.getVehicleId(), actor.id(),
                now));
        if (file != null) {
            store(dispute, DisputeParty.OWNER, actor, file, now);
        }
        return view(dispute, DisputeParty.OWNER, vehicle(dispute.getVehicleId()), now);
    }

    /** Either party, until the dispute is decided. */
    @Transactional
    public PartyDisputeView addEvidence(AuthenticatedUser actor, UUID disputeId, EvidenceUpload file) {
        OwnershipDispute dispute = asParty(actor, disputeId);
        if (!dispute.isUndecided()) {
            throw new DisputeClosedException("This dispute has already been decided.");
        }
        if (file == null) {
            throw new InvalidDisputeException("No file.");
        }
        DisputeParty party = partyOf(dispute, actor);
        Instant now = calendar.now();
        store(dispute, party, actor, file, now);
        return view(dispute, party, vehicle(dispute.getVehicleId()), now);
    }

    /** Disputes the caller filed or that are about their ownership, newest first. */
    @Transactional(readOnly = true)
    public List<PartyDisputeView> mine(AuthenticatedUser actor) {
        List<OwnershipDispute> list = disputes.findByClaimantIdOrOwnerIdOrderByCreatedAtDesc(actor.id(), actor.id());
        if (list.isEmpty()) {
            return List.of();
        }
        Set<UUID> vehicleIds = list.stream().map(OwnershipDispute::getVehicleId).collect(Collectors.toSet());
        Map<UUID, VehicleSummary> vehicles = vehicleDirectory.findSummaries(vehicleIds).stream()
                .collect(Collectors.toMap(VehicleSummary::id, Function.identity()));
        Map<UUID, List<DisputeEvidence>> evidenceByDispute = evidence.findByDisputeIdInOrderByUploadedAtAsc(
                        list.stream().map(OwnershipDispute::getId).toList()).stream()
                .collect(Collectors.groupingBy(DisputeEvidence::getDisputeId));
        Instant now = calendar.now();
        return list.stream()
                .map(d -> view(d, partyOf(d, actor), vehicles.get(d.getVehicleId()),
                        evidenceByDispute.getOrDefault(d.getId(), List.of()), now))
                .toList();
    }

    private void store(OwnershipDispute dispute, DisputeParty party, AuthenticatedUser actor, EvidenceUpload file,
                       Instant now) {
        if (evidence.countByDisputeIdAndParty(dispute.getId(), party) >= properties.maxEvidencePerParty()) {
            throw new TooMuchEvidenceException(properties.maxEvidencePerParty());
        }
        StoredEvidence stored = files.store("disputes/" + dispute.getId(), file.fileName(), file.size(),
                file.content());
        DisputeEvidence saved = evidence.save(DisputeEvidence.of(dispute.getId(), party, actor.id(),
                stored.fileName(), stored.storageKey(), stored.mimeType(), stored.size(), stored.sha256(), now));
        events.publishEvent(new DisputeEvents.DisputeEvidenceAdded(dispute.getId(), saved.getId(), party.name(),
                actor.id(), stored.mimeType(), stored.size(), stored.sha256(), now));
    }

    private OwnershipDispute asParty(AuthenticatedUser actor, UUID disputeId) {
        return disputes.findById(disputeId)
                .filter(d -> d.getClaimantId().equals(actor.id()) || d.getOwnerId().equals(actor.id()))
                .orElseThrow(DisputeNotFoundException::new);
    }

    private static DisputeParty partyOf(OwnershipDispute dispute, AuthenticatedUser actor) {
        return dispute.getClaimantId().equals(actor.id()) ? DisputeParty.CLAIMANT : DisputeParty.OWNER;
    }

    private VehicleSummary vehicle(UUID vehicleId) {
        return vehicleDirectory.findSummaries(List.of(vehicleId)).stream().findFirst().orElse(null);
    }

    private PartyDisputeView view(OwnershipDispute dispute, DisputeParty role, VehicleSummary vehicle, Instant now) {
        return view(dispute, role, vehicle, evidence.findByDisputeIdInOrderByUploadedAtAsc(List.of(dispute.getId())),
                now);
    }

    private static PartyDisputeView view(OwnershipDispute d, DisputeParty role, VehicleSummary vehicle,
                                         List<DisputeEvidence> allEvidence, Instant now) {
        boolean owner = role == DisputeParty.OWNER;
        List<EvidenceView> mine = allEvidence.stream().filter(e -> e.getParty() == role).map(EvidenceView::of)
                .toList();
        return new PartyDisputeView(d.getId(), role, VehicleInfo.of(vehicle), d.getStatus(), d.getCreatedAt(),
                d.getResponseDeadline(), owner && d.canRespond(now), d.isUndecided(),
                owner ? d.getOwnerStatement() : d.getClaimantStatement(), mine, d.getDecidedAt(),
                d.getDecisionNote(), d.getNewOwnerSince());
    }
}
