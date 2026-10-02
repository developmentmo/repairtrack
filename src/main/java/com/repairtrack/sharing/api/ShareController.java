package com.repairtrack.sharing.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.repairtrack.security.AuthenticatedUser;
import com.repairtrack.sharing.application.ShareService;
import com.repairtrack.sharing.application.ShareViews.CreatedShare;
import com.repairtrack.sharing.application.ShareViews.ShareView;
import com.repairtrack.sharing.domain.ShareStatus;

/** Share-link management for the vehicle's current owner. */
@RestController
class ShareController {

    private final ShareService shareService;

    ShareController(ShareService shareService) {
        this.shareService = shareService;
    }

    /** Body optional: defaults to 30 days, without documents. */
    @PostMapping("/api/v1/vehicles/{vehicleId}/shares")
    @ResponseStatus(HttpStatus.CREATED)
    CreatedShareResponse create(@AuthenticationPrincipal AuthenticatedUser actor,
                                @PathVariable("vehicleId") UUID vehicleId,
                                @Valid @RequestBody(required = false) CreateShareRequest request) {
        CreateShareRequest r = request == null ? new CreateShareRequest(null, null) : request;
        CreatedShare created = shareService.create(actor, vehicleId, r.validDays(),
                Boolean.TRUE.equals(r.includeDocuments()));
        return new CreatedShareResponse(ShareResponse.from(created.share()), created.token(), created.url());
    }

    @GetMapping("/api/v1/vehicles/{vehicleId}/shares")
    List<ShareResponse> list(@AuthenticationPrincipal AuthenticatedUser actor,
                             @PathVariable("vehicleId") UUID vehicleId) {
        return shareService.list(actor, vehicleId).stream().map(ShareResponse::from).toList();
    }

    @PostMapping("/api/v1/shares/{shareId}/revoke")
    ShareResponse revoke(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable("shareId") UUID shareId) {
        return ShareResponse.from(shareService.revoke(actor, shareId));
    }

    public record CreateShareRequest(@Min(1) @Max(365) Integer validDays, Boolean includeDocuments) {
    }

    /** {@code token}/{@code url} are returned only here, once. Store or send the link now. */
    public record CreatedShareResponse(ShareResponse share, String token, String url) {
    }

    public record ShareResponse(UUID id, Instant createdAt, Instant expiresAt, boolean includeDocuments,
                                ShareStatus status, long accessCount, Instant lastAccessedAt) {

        static ShareResponse from(ShareView v) {
            return new ShareResponse(v.id(), v.createdAt(), v.expiresAt(), v.includeDocuments(), v.status(),
                    v.accessCount(), v.lastAccessedAt());
        }
    }
}
