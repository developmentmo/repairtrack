package com.repairtrack.dispute.api;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.repairtrack.dispute.application.DisputeAdminService;
import com.repairtrack.dispute.application.DisputeViews.AdminDisputeView;
import com.repairtrack.dispute.application.DisputeViews.EvidenceDownload;
import com.repairtrack.security.AuthenticatedUser;

/** SYSTEM_ADMIN only (checked in the service). */
@RestController
class AdminDisputeController {

    enum Decision { UPHOLD, REJECT }

    /**
     * {@code note} is sent to both parties. {@code newOwnerSince} (upheld only): start of the claimant's ownership,
     * default today; may lie before the revoked period.
     */
    record DecisionRequest(@NotNull Decision decision, @NotNull @Size(max = 2000) String note,
                           LocalDate newOwnerSince) {
    }

    private final DisputeAdminService adminService;

    AdminDisputeController(DisputeAdminService adminService) {
        this.adminService = adminService;
    }

    /** {@code state=open} (default, the review queue) or {@code decided}. */
    @GetMapping("/api/v1/admin/disputes")
    List<AdminDisputeView> list(@AuthenticationPrincipal AuthenticatedUser actor,
                                @RequestParam(name = "state", defaultValue = "open") String state) {
        return adminService.list(actor, !"decided".equalsIgnoreCase(state));
    }

    @GetMapping("/api/v1/admin/disputes/{disputeId}")
    AdminDisputeView get(@AuthenticationPrincipal AuthenticatedUser actor,
                         @PathVariable("disputeId") UUID disputeId) {
        return adminService.get(actor, disputeId);
    }

    /** Presigned download link, valid for a few minutes. */
    @GetMapping("/api/v1/admin/disputes/{disputeId}/evidence/{evidenceId}")
    EvidenceDownload evidence(@AuthenticationPrincipal AuthenticatedUser actor,
                              @PathVariable("disputeId") UUID disputeId,
                              @PathVariable("evidenceId") UUID evidenceId) {
        return adminService.download(actor, disputeId, evidenceId);
    }

    @PostMapping("/api/v1/admin/disputes/{disputeId}/decision")
    AdminDisputeView decide(@AuthenticationPrincipal AuthenticatedUser actor,
                            @PathVariable("disputeId") UUID disputeId,
                            @Valid @RequestBody DecisionRequest request) {
        return adminService.decide(actor, disputeId, request.decision() == Decision.UPHOLD, request.note(),
                request.newOwnerSince());
    }
}
