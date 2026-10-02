package com.repairtrack.repair.api;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.repairtrack.repair.api.Requests.AddPartsRequest;
import com.repairtrack.repair.api.Requests.CorrectRepairRequest;
import com.repairtrack.repair.api.Requests.CreateRepairRequest;
import com.repairtrack.repair.api.Requests.PartRequest;
import com.repairtrack.repair.api.Requests.VoidRepairRequest;
import com.repairtrack.repair.api.Responses.MileageHistoryResponse;
import com.repairtrack.repair.api.Responses.PartResponse;
import com.repairtrack.repair.api.Responses.RepairResponse;
import com.repairtrack.repair.application.Commands;
import com.repairtrack.repair.application.RepairQueryService;
import com.repairtrack.repair.application.RepairService;
import com.repairtrack.repair.application.Views.RepairResult;
import com.repairtrack.repair.domain.RepairChanges;
import com.repairtrack.security.AuthenticatedUser;

/**
 * Vehicle history endpoints. There is intentionally no DELETE: records are voided
 * ({@code POST /repairs/{id}/void}) or corrected ({@code POST /repairs/{id}/corrections}).
 */
@RestController
@RequestMapping("/api/v1")
class RepairController {

    private final RepairService repairService;
    private final RepairQueryService queryService;

    RepairController(RepairService repairService, RepairQueryService queryService) {
        this.repairService = repairService;
        this.queryService = queryService;
    }

    @PostMapping("/vehicles/{vehicleId}/repairs")
    @ResponseStatus(HttpStatus.CREATED)
    RepairResponse create(@AuthenticationPrincipal AuthenticatedUser actor,
                          @PathVariable("vehicleId") UUID vehicleId,
                          @Valid @RequestBody CreateRepairRequest request) {
        var command = new Commands.CreateRepair(request.eventType(), request.eventDate(), request.mileage(),
                request.title(), request.description(), request.garageId(), toCommands(request.parts()));
        RepairResult result = repairService.create(actor, vehicleId, command);
        return RepairResponse.from(result.repair(), result.warnings());
    }

    @GetMapping("/vehicles/{vehicleId}/repairs")
    List<RepairResponse> history(@AuthenticationPrincipal AuthenticatedUser actor,
                                 @PathVariable("vehicleId") UUID vehicleId) {
        return queryService.history(actor, vehicleId).stream().map(RepairResponse::from).toList();
    }

    @GetMapping("/vehicles/{vehicleId}/mileage")
    MileageHistoryResponse mileage(@AuthenticationPrincipal AuthenticatedUser actor,
                                   @PathVariable("vehicleId") UUID vehicleId) {
        return MileageHistoryResponse.from(queryService.mileage(actor, vehicleId));
    }

    @GetMapping("/repairs/{repairId}")
    RepairResponse get(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable("repairId") UUID repairId) {
        return RepairResponse.from(queryService.get(actor, repairId));
    }

    @PostMapping("/repairs/{repairId}/void")
    RepairResponse voidRepair(@AuthenticationPrincipal AuthenticatedUser actor,
                              @PathVariable("repairId") UUID repairId,
                              @Valid @RequestBody VoidRepairRequest request) {
        return RepairResponse.from(repairService.voidRepair(actor, repairId, request.reason()));
    }

    @PostMapping("/repairs/{repairId}/corrections")
    RepairResponse correct(@AuthenticationPrincipal AuthenticatedUser actor,
                           @PathVariable("repairId") UUID repairId,
                           @Valid @RequestBody CorrectRepairRequest request) {
        var changes = new RepairChanges(request.eventType(), request.eventDate(), request.mileage(), request.title(),
                request.description());
        RepairResult result = repairService.correct(actor, repairId, changes, request.reason());
        return RepairResponse.from(result.repair(), result.warnings());
    }

    @PostMapping("/repairs/{repairId}/parts")
    @ResponseStatus(HttpStatus.CREATED)
    RepairResponse addParts(@AuthenticationPrincipal AuthenticatedUser actor,
                            @PathVariable("repairId") UUID repairId,
                            @Valid @RequestBody AddPartsRequest request) {
        return RepairResponse.from(repairService.addParts(actor, repairId, toCommands(request.parts())));
    }

    @GetMapping("/repairs/{repairId}/parts")
    List<PartResponse> parts(@AuthenticationPrincipal AuthenticatedUser actor,
                             @PathVariable("repairId") UUID repairId) {
        return queryService.parts(actor, repairId).stream().map(PartResponse::from).toList();
    }

    private static List<Commands.AddPart> toCommands(List<PartRequest> parts) {
        if (parts == null) {
            return List.of();
        }
        return parts.stream()
                .map(p -> new Commands.AddPart(p.partNumber(), p.brand(), p.description(), p.quantity()))
                .toList();
    }
}
