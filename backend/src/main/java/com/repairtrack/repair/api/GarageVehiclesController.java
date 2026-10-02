package com.repairtrack.repair.api;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.repairtrack.repair.api.Responses.VehicleSummaryResponse;
import com.repairtrack.repair.application.RepairQueryService;
import com.repairtrack.security.AuthenticatedUser;

/**
 * Lives in the repair module (not garage) because "vehicles a garage worked on" is derived from
 * repair records; the garage module must not depend on repairs.
 */
@RestController
class GarageVehiclesController {

    private final RepairQueryService queryService;

    GarageVehiclesController(RepairQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/api/v1/garages/{garageId}/vehicles")
    List<VehicleSummaryResponse> vehicles(@AuthenticationPrincipal AuthenticatedUser actor,
                                          @PathVariable("garageId") UUID garageId) {
        return queryService.garageVehicles(actor, garageId).stream().map(VehicleSummaryResponse::from).toList();
    }
}
