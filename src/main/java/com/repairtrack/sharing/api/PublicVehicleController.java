package com.repairtrack.sharing.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.repairtrack.sharing.application.PublicHistory;
import com.repairtrack.sharing.application.PublicHistoryService;

/**
 * Public, unauthenticated endpoints behind a share link (security config permits GET /api/v1/public/**).
 * The path segment is the share token, never an internal ID. Responses use dedicated public models,
 * never entities or internal views.
 */
@RestController
class PublicVehicleController {

    private final PublicHistoryService publicHistoryService;

    PublicVehicleController(PublicHistoryService publicHistoryService) {
        this.publicHistoryService = publicHistoryService;
    }

    @GetMapping("/api/v1/public/vehicles/{token}")
    PublicHistory.Report history(@PathVariable("token") String token) {
        return publicHistoryService.report(token);
    }

    /** {@code reference}: a document's SHA-256 from the report. Only when the owner allowed downloads. */
    @GetMapping("/api/v1/public/vehicles/{token}/documents/{reference}")
    PublicHistory.DocumentLink document(@PathVariable("token") String token,
                                        @PathVariable("reference") String reference) {
        return publicHistoryService.documentLink(token, reference);
    }
}
