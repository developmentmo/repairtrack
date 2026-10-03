package com.repairtrack.dispute.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.repairtrack.dispute.application.DisputeService;
import com.repairtrack.dispute.application.DisputeViews.PartyDisputeView;
import com.repairtrack.dispute.application.EvidenceUpload;
import com.repairtrack.security.AuthenticatedUser;

/**
 * The parties' endpoints. Filing and responding are {@code multipart/form-data}: text fields plus a {@code file}
 * part (PDF, JPEG or PNG; the same checks as documents).
 */
@RestController
class DisputeController {

    private final DisputeService disputeService;

    DisputeController(DisputeService disputeService) {
        this.disputeService = disputeService;
    }

    /** File a dispute: {@code vin} (full, as proof), {@code statement}, {@code file} (required). */
    @PostMapping(path = "/api/v1/vehicles/{vehicleId}/disputes", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    PartyDisputeView open(@AuthenticationPrincipal AuthenticatedUser actor,
                          @PathVariable("vehicleId") UUID vehicleId,
                          @RequestParam("vin") String vin,
                          @RequestParam("statement") String statement,
                          @RequestPart(name = "file", required = false) MultipartFile file) {
        return disputeService.open(actor, vehicleId, vin, statement, upload(file));
    }

    /** The contested owner: {@code statement} and optionally a {@code file}. */
    @PostMapping(path = "/api/v1/disputes/{disputeId}/response", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    PartyDisputeView respond(@AuthenticationPrincipal AuthenticatedUser actor,
                             @PathVariable("disputeId") UUID disputeId,
                             @RequestParam("statement") String statement,
                             @RequestPart(name = "file", required = false) MultipartFile file) {
        return disputeService.respond(actor, disputeId, statement, upload(file));
    }

    @PostMapping(path = "/api/v1/disputes/{disputeId}/evidence", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    PartyDisputeView addEvidence(@AuthenticationPrincipal AuthenticatedUser actor,
                                 @PathVariable("disputeId") UUID disputeId,
                                 @RequestPart(name = "file", required = false) MultipartFile file) {
        return disputeService.addEvidence(actor, disputeId, upload(file));
    }

    @GetMapping("/api/v1/disputes/mine")
    List<PartyDisputeView> mine(@AuthenticationPrincipal AuthenticatedUser actor) {
        return disputeService.mine(actor);
    }

    /** The multipart file is spooled by the servlet container, so it can be read more than once. */
    private static EvidenceUpload upload(MultipartFile file) {
        return file == null ? null : new EvidenceUpload(file.getOriginalFilename(), file.getSize(), file::getInputStream);
    }
}
