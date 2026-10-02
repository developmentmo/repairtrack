package com.repairtrack.garage.api;

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

import com.repairtrack.garage.application.GarageService;
import com.repairtrack.garage.application.RegisterGarageCommand;
import com.repairtrack.security.AuthenticatedUser;

@RestController
@RequestMapping("/api/v1/garages")
class GarageController {

    private final GarageService garageService;

    GarageController(GarageService garageService) {
        this.garageService = garageService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    GarageResponse register(@AuthenticationPrincipal AuthenticatedUser actor,
                            @Valid @RequestBody RegisterGarageRequest request) {
        var command = new RegisterGarageCommand(request.name(), request.kvkNumber(), request.address(),
                request.postalCode(), request.city(), request.phone(), request.email());
        return GarageResponse.from(garageService.register(actor, command));
    }

    /** Garages the caller works at, with their role. Literal path wins over {garageId}. */
    @GetMapping("/mine")
    List<MyGarageResponse> mine(@AuthenticationPrincipal AuthenticatedUser actor) {
        return garageService.myGarages(actor).stream().map(MyGarageResponse::from).toList();
    }

    @GetMapping("/{garageId}")
    GarageResponse get(@PathVariable("garageId") UUID garageId) {
        return GarageResponse.from(garageService.get(garageId));
    }

    /** Garage admin re-applies for verification (UNVERIFIED -> PENDING). */
    @PostMapping("/{garageId}/verification-request")
    GarageResponse requestVerification(@AuthenticationPrincipal AuthenticatedUser actor,
                                       @PathVariable("garageId") UUID garageId) {
        return GarageResponse.from(garageService.requestVerification(actor, garageId));
    }

    /** System admin decision: VERIFIED, UNVERIFIED or SUSPENDED. */
    @PostMapping("/{garageId}/verification")
    GarageResponse decideVerification(@AuthenticationPrincipal AuthenticatedUser actor,
                                      @PathVariable("garageId") UUID garageId,
                                      @Valid @RequestBody VerificationDecisionRequest request) {
        return GarageResponse.from(
                garageService.decideVerification(actor, garageId, request.status(), request.note()));
    }
}
