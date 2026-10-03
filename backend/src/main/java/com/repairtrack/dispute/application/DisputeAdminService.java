package com.repairtrack.dispute.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.repairtrack.common.time.BusinessCalendar;
import com.repairtrack.dispute.DisputeEvents;
import com.repairtrack.dispute.application.DisputeExceptions.DisputeNotFoundException;
import com.repairtrack.dispute.application.DisputeExceptions.SystemAdminRequiredException;
import com.repairtrack.dispute.application.DisputeViews.AdminDisputeView;
import com.repairtrack.dispute.application.DisputeViews.EvidenceDownload;
import com.repairtrack.dispute.application.DisputeViews.EvidenceView;
import com.repairtrack.dispute.application.DisputeViews.PartyInfo;
import com.repairtrack.dispute.application.DisputeViews.VehicleInfo;
import com.repairtrack.dispute.domain.DisputeEvidence;
import com.repairtrack.dispute.domain.DisputeStatus;
import com.repairtrack.dispute.domain.OwnershipDispute;
import com.repairtrack.dispute.infrastructure.DisputeEvidenceRepository;
import com.repairtrack.dispute.infrastructure.OwnershipDisputeRepository;
import com.repairtrack.document.EvidenceFiles;
import com.repairtrack.security.AuthenticatedUser;
import com.repairtrack.security.Role;
import com.repairtrack.security.UserDirectory;
import com.repairtrack.security.UserSummary;
import com.repairtrack.vehicle.OwnershipDisputeSupport;
import com.repairtrack.vehicle.VehicleDirectory;
import com.repairtrack.vehicle.VehicleSummary;

/** System admins: review queue, evidence downloads and the (final) decision. */
@Service
public class DisputeAdminService {

    private static final Set<DisputeStatus> DECIDED = EnumSet.of(DisputeStatus.UPHELD, DisputeStatus.REJECTED);

    private final OwnershipDisputeRepository disputes;
    private final DisputeEvidenceRepository evidence;
    private final OwnershipDisputeSupport ownership;
    private final VehicleDirectory vehicleDirectory;
    private final UserDirectory users;
    private final EvidenceFiles files;
    private final DisputeMails mails;
    private final ApplicationEventPublisher events;
    private final BusinessCalendar calendar;

    public DisputeAdminService(OwnershipDisputeRepository disputes, DisputeEvidenceRepository evidence,
                               OwnershipDisputeSupport ownership, VehicleDirectory vehicleDirectory,
                               UserDirectory users, EvidenceFiles files, DisputeMails mails,
                               ApplicationEventPublisher events, BusinessCalendar calendar) {
        this.disputes = disputes;
        this.evidence = evidence;
        this.ownership = ownership;
        this.vehicleDirectory = vehicleDirectory;
        this.users = users;
        this.files = files;
        this.mails = mails;
        this.events = events;
        this.calendar = calendar;
    }

    /** {@code undecided}: the queue, oldest first; otherwise the latest 100 decisions. */
    @Transactional(readOnly = true)
    public List<AdminDisputeView> list(AuthenticatedUser actor, boolean undecided) {
        requireSystemAdmin(actor);
        List<OwnershipDispute> list = undecided
                ? disputes.findByStatusInOrderByCreatedAtAsc(DisputeStatus.UNDECIDED)
                : disputes.findTop100ByStatusInOrderByDecidedAtDesc(DECIDED);
        return views(list);
    }

    @Transactional(readOnly = true)
    public AdminDisputeView get(AuthenticatedUser actor, UUID disputeId) {
        requireSystemAdmin(actor);
        return views(List.of(find(disputeId))).getFirst();
    }

    @Transactional(readOnly = true)
    public EvidenceDownload download(AuthenticatedUser actor, UUID disputeId, UUID evidenceId) {
        requireSystemAdmin(actor);
        DisputeEvidence file = evidence.findById(evidenceId)
                .filter(e -> e.getDisputeId().equals(disputeId))
                .orElseThrow(DisputeNotFoundException::new);
        return new EvidenceDownload(EvidenceView.of(file),
                files.presignDownload(file.getStorageKey(), file.getFileName(), file.getMimeType()).toString(),
                calendar.now().plus(files.presignedUrlTtl()));
    }

    /**
     * Upheld: the contested ownership is revoked and the claimant becomes the owner from {@code newOwnerSince}
     * (default today). Rejected: nothing changes. Both parties get an email with {@code note}.
     */
    @Transactional
    public AdminDisputeView decide(AuthenticatedUser actor, UUID disputeId, boolean upheld, String note,
                                   LocalDate newOwnerSince) {
        requireSystemAdmin(actor);
        OwnershipDispute dispute = find(disputeId);
        Instant now = calendar.now();
        if (upheld) {
            LocalDate since = newOwnerSince != null ? newOwnerSince : calendar.today();
            dispute.uphold(actor.id(), note, since, now);
            ownership.transferAfterUpheldDispute(dispute.getId(), dispute.getVehicleId(),
                    dispute.getContestedOwnershipId(), dispute.getClaimantId(), since, actor.id());
        } else {
            dispute.reject(actor.id(), note, now);
        }
        disputes.save(dispute);
        events.publishEvent(new DisputeEvents.DisputeDecided(dispute.getId(), dispute.getVehicleId(), upheld,
                actor.id(), dispute.getNewOwnerSince(), now));
        mails.decided(dispute, vehicleDirectory.findSummaries(List.of(dispute.getVehicleId())).stream()
                .findFirst().orElse(null));
        return views(List.of(dispute)).getFirst();
    }

    private OwnershipDispute find(UUID disputeId) {
        return disputes.findById(disputeId).orElseThrow(DisputeNotFoundException::new);
    }

    private List<AdminDisputeView> views(List<OwnershipDispute> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        Set<UUID> vehicleIds = list.stream().map(OwnershipDispute::getVehicleId).collect(Collectors.toSet());
        Map<UUID, VehicleSummary> vehicles = vehicleDirectory.findSummaries(vehicleIds).stream()
                .collect(Collectors.toMap(VehicleSummary::id, Function.identity()));
        Set<UUID> userIds = new HashSet<>();
        list.forEach(d -> {
            userIds.add(d.getClaimantId());
            userIds.add(d.getOwnerId());
        });
        Map<UUID, UserSummary> people = users.findByIds(userIds);
        Map<UUID, List<DisputeEvidence>> evidenceByDispute = evidence.findByDisputeIdInOrderByUploadedAtAsc(
                        list.stream().map(OwnershipDispute::getId).toList()).stream()
                .collect(Collectors.groupingBy(DisputeEvidence::getDisputeId));
        Instant now = calendar.now();
        return list.stream().map(d -> new AdminDisputeView(
                d.getId(),
                VehicleInfo.of(vehicles.get(d.getVehicleId())),
                d.getStatus(),
                d.isReviewable(now),
                party(people.get(d.getClaimantId())),
                party(people.get(d.getOwnerId())),
                d.getClaimantStatement(),
                d.getOwnerStatement(),
                d.getOwnerRespondedAt(),
                d.getCreatedAt(),
                d.getResponseDeadline(),
                evidenceByDispute.getOrDefault(d.getId(), List.of()).stream().map(EvidenceView::of).toList(),
                d.getDecidedBy(),
                d.getDecidedAt(),
                d.getDecisionNote(),
                d.getNewOwnerSince())).toList();
    }

    private static PartyInfo party(UserSummary user) {
        return user == null ? null : new PartyInfo(user.id(), user.email(), user.firstName(), user.lastName());
    }

    private static void requireSystemAdmin(AuthenticatedUser actor) {
        if (!actor.hasRole(Role.SYSTEM_ADMIN)) {
            throw new SystemAdminRequiredException();
        }
    }
}
