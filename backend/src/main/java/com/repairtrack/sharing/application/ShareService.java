package com.repairtrack.sharing.application;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.repairtrack.common.crypto.OpaqueTokens;
import com.repairtrack.common.time.BusinessCalendar;
import com.repairtrack.dispute.DisputeDirectory;
import com.repairtrack.security.AuthenticatedUser;
import com.repairtrack.sharing.SharingEvents;
import com.repairtrack.sharing.application.ShareViews.CreatedShare;
import com.repairtrack.sharing.application.ShareViews.ShareView;
import com.repairtrack.sharing.domain.VehicleShare;
import com.repairtrack.sharing.infrastructure.SharingProperties;
import com.repairtrack.sharing.infrastructure.VehicleShareRepository;
import com.repairtrack.vehicle.VehicleAccessService;

/** Owners create, list and revoke share links for their vehicle. */
@Service
public class ShareService {

    private final VehicleShareRepository shares;
    private final VehicleAccessService vehicleAccess;
    private final DisputeDirectory disputeDirectory;
    private final SharingProperties properties;
    private final ApplicationEventPublisher events;
    private final BusinessCalendar calendar;

    public ShareService(VehicleShareRepository shares, VehicleAccessService vehicleAccess,
                        DisputeDirectory disputeDirectory, SharingProperties properties,
                        ApplicationEventPublisher events, BusinessCalendar calendar) {
        this.shares = shares;
        this.vehicleAccess = vehicleAccess;
        this.disputeDirectory = disputeDirectory;
        this.properties = properties;
        this.events = events;
        this.calendar = calendar;
    }

    /**
     * Only the current owner can share. The token (256 bits, random) is returned once; only its
     * SHA-256 hash is stored, so a database leak does not reveal working links. Not while the ownership is
     * disputed (existing links keep working and show that the ownership is under review).
     */
    @Transactional
    public CreatedShare create(AuthenticatedUser actor, UUID vehicleId, Integer validDays, boolean includeDocuments) {
        vehicleAccess.requireActiveOwner(actor, vehicleId);
        if (disputeDirectory.isUnderDispute(vehicleId)) {
            throw new VehicleUnderDisputeException();
        }
        Duration validity = validDays == null ? properties.defaultValidity() : Duration.ofDays(validDays);
        Instant now = calendar.now();
        String token = OpaqueTokens.generate();
        VehicleShare share = VehicleShare.create(vehicleId, OpaqueTokens.sha256Hex(token), includeDocuments, validity,
                actor.id(), now);
        shares.save(share);
        events.publishEvent(new SharingEvents.ShareCreated(share.getId(), vehicleId, actor.id(), share.getExpiresAt(),
                includeDocuments, now));
        return new CreatedShare(view(share, now, true), token, properties.publicBaseUrl() + "/v/" + token);
    }

    /** The caller's own links for this vehicle (links of previous owners are not shown). */
    @Transactional(readOnly = true)
    public List<ShareView> list(AuthenticatedUser actor, UUID vehicleId) {
        vehicleAccess.requireActiveOwner(actor, vehicleId);
        Instant now = calendar.now();
        return shares.findByVehicleIdAndCreatedByOrderByCreatedAtDesc(vehicleId, actor.id()).stream()
                .map(share -> view(share, now, true))
                .toList();
    }

    /** The vehicle's current owner can revoke its links. Idempotent. */
    @Transactional
    public ShareView revoke(AuthenticatedUser actor, UUID shareId) {
        VehicleShare share = shares.findById(shareId).orElseThrow(ShareNotFoundException::new);
        vehicleAccess.requireActiveOwner(actor, share.getVehicleId());
        Instant now = calendar.now();
        if (share.revoke(actor.id(), now)) {
            events.publishEvent(new SharingEvents.ShareRevoked(share.getId(), share.getVehicleId(), actor.id(), now));
        }
        return view(share, now, vehicleAccess.isActiveOwner(share.getCreatedBy(), share.getVehicleId()));
    }

    private static ShareView view(VehicleShare share, Instant now, boolean creatorIsCurrentOwner) {
        return new ShareView(share.getId(), share.getCreatedAt(), share.getExpiresAt(), share.isIncludeDocuments(),
                share.status(now, creatorIsCurrentOwner), share.getAccessCount(), share.getLastAccessedAt());
    }
}
