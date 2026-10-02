package com.repairtrack.sharing.application;

import java.time.Instant;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.repairtrack.common.crypto.OpaqueTokens;
import com.repairtrack.common.time.BusinessCalendar;
import com.repairtrack.document.DocumentDirectory;
import com.repairtrack.document.DocumentSummary;
import com.repairtrack.garage.GarageSummary;
import com.repairtrack.mileage.MileageHistory;
import com.repairtrack.mileage.MileageReading;
import com.repairtrack.mileage.MileageService;
import com.repairtrack.repair.VehicleHistory;
import com.repairtrack.repair.VehicleHistoryReader;
import com.repairtrack.sharing.application.PublicHistory.Report;
import com.repairtrack.sharing.domain.ShareStatus;
import com.repairtrack.sharing.domain.VehicleShare;
import com.repairtrack.sharing.infrastructure.VehicleShareRepository;
import com.repairtrack.vehicle.VehicleAccessService;
import com.repairtrack.vehicle.VehicleDirectory;
import com.repairtrack.vehicle.VehiclePublicProfile;
import com.repairtrack.verification.VerificationStatus;

/**
 * Serves the public report behind a share link, without login. The token is the only credential:
 * it is hashed and looked up; any link that is not ACTIVE gets the same "not found" answer.
 */
@Service
public class PublicHistoryService {

    private final VehicleShareRepository shares;
    private final VehicleAccessService vehicleAccess;
    private final VehicleDirectory vehicleDirectory;
    private final VehicleHistoryReader historyReader;
    private final MileageService mileageService;
    private final DocumentDirectory documentDirectory;
    private final BusinessCalendar calendar;

    public PublicHistoryService(VehicleShareRepository shares, VehicleAccessService vehicleAccess,
                                VehicleDirectory vehicleDirectory, VehicleHistoryReader historyReader,
                                MileageService mileageService, DocumentDirectory documentDirectory,
                                BusinessCalendar calendar) {
        this.shares = shares;
        this.vehicleAccess = vehicleAccess;
        this.vehicleDirectory = vehicleDirectory;
        this.historyReader = historyReader;
        this.mileageService = mileageService;
        this.documentDirectory = documentDirectory;
        this.calendar = calendar;
    }

    @Transactional
    public Report report(String token) {
        Instant now = calendar.now();
        VehicleShare share = activeShare(token, now);
        shares.recordAccess(share.getId(), now);
        UUID vehicleId = share.getVehicleId();

        VehiclePublicProfile profile = vehicleDirectory.publicProfile(vehicleId);
        List<VehicleHistory.Entry> history = historyReader.history(vehicleId);
        MileageHistory mileage = mileageService.history(vehicleId);
        Map<UUID, List<DocumentSummary>> documentsByRepair = documentDirectory.forVehicle(vehicleId).stream()
                .collect(Collectors.groupingBy(DocumentSummary::repairEventId));
        boolean downloadable = share.isIncludeDocuments();

        List<PublicHistory.Entry> entries = history.stream()
                .map(entry -> toEntry(entry, documentsByRepair.getOrDefault(entry.repairId(), List.of()), downloadable))
                .toList();

        return new Report(
                new PublicHistory.Vehicle(profile.make(), profile.model(), profile.modelYear(),
                        profile.firstRegistrationDate(), profile.licensePlate(), profile.registeredOwnerCount()),
                summary(entries, mileage),
                entries,
                new PublicHistory.Mileage(
                        mileage.readings().stream().map(PublicHistoryService::reading).toList(),
                        mileage.anomalies().stream().map(a -> new PublicHistory.Inconsistency(a.message(),
                                reading(a.earlier()), reading(a.later()))).toList()),
                downloadable,
                now,
                share.getExpiresAt());
    }

    /** Download link for a document of the shared vehicle, only if the owner allowed downloads. */
    @Transactional
    public PublicHistory.DocumentLink documentLink(String token, String reference) {
        Instant now = calendar.now();
        VehicleShare share = activeShare(token, now);
        if (!share.isIncludeDocuments()) {
            throw new ShareNotFoundException();
        }
        return documentDirectory.presignForVehicle(share.getVehicleId(), reference)
                .map(url -> new PublicHistory.DocumentLink(url.toString(), now.plus(documentDirectory.presignedUrlTtl())))
                .orElseThrow(ShareNotFoundException::new);
    }

    private VehicleShare activeShare(String token, Instant now) {
        if (token == null || token.isBlank() || token.length() > 100) {
            throw new ShareNotFoundException();
        }
        VehicleShare share = shares.findByTokenHash(OpaqueTokens.sha256Hex(token))
                .orElseThrow(ShareNotFoundException::new);
        boolean creatorIsOwner = vehicleAccess.isActiveOwner(share.getCreatedBy(), share.getVehicleId());
        if (share.status(now, creatorIsOwner) != ShareStatus.ACTIVE) {
            throw new ShareNotFoundException();
        }
        return share;
    }

    private static PublicHistory.Entry toEntry(VehicleHistory.Entry e, List<DocumentSummary> documents,
                                               boolean downloadable) {
        return new PublicHistory.Entry(
                e.eventType(), e.eventDate(), e.mileage(), e.title(), e.description(), e.sourceType(),
                e.verificationStatus(), e.voided(), e.voidReason(),
                garage(e.garage()),
                e.parts().stream().map(p -> new PublicHistory.Part(p.partNumber(), p.brand(), p.description(),
                        p.quantity())).toList(),
                e.corrections().stream().map(c -> new PublicHistory.Correction(c.field(), c.originalValue(),
                        c.correctedValue(), c.reason(),
                        c.correctedByGarage() == null ? "OWNER" : c.correctedByGarage().name(), c.correctedAt()))
                        .toList(),
                documents.stream().map(d -> new PublicHistory.Document(d.documentType(), d.mimeType(), d.fileSize(),
                        d.uploadedAt(), downloadable, downloadable ? d.sha256() : null)).toList());
    }

    private static PublicHistory.Garage garage(GarageSummary garage) {
        return garage == null ? null : new PublicHistory.Garage(garage.name(), garage.city(), garage.verificationStatus());
    }

    private static PublicHistory.Reading reading(MileageReading reading) {
        return new PublicHistory.Reading(reading.recordedDate(), reading.mileage(), reading.sourceType());
    }

    private static PublicHistory.Summary summary(List<PublicHistory.Entry> entries, MileageHistory mileage) {
        List<PublicHistory.Entry> active = entries.stream().filter(e -> !e.voided()).toList();
        Map<VerificationStatus, Long> byVerification = new EnumMap<>(VerificationStatus.class);
        for (VerificationStatus status : VerificationStatus.values()) {
            byVerification.put(status, active.stream().filter(e -> e.verificationStatus() == status).count());
        }
        Integer lastMileage = mileage.readings().isEmpty() ? null
                : mileage.readings().getLast().mileage();
        return new PublicHistory.Summary(
                entries.size(),
                entries.size() - active.size(),
                byVerification,
                active.stream().map(PublicHistory.Entry::eventDate).min(Comparator.naturalOrder()).orElse(null),
                active.stream().map(PublicHistory.Entry::eventDate).max(Comparator.naturalOrder()).orElse(null),
                lastMileage,
                mileage.anomalies().size(),
                (int) entries.stream().mapToLong(e -> e.documents().size()).sum());
    }
}
