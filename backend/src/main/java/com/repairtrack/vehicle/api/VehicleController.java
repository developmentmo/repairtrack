package com.repairtrack.vehicle.api;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.repairtrack.security.AuthenticatedUser;
import com.repairtrack.vehicle.application.OwnershipService;
import com.repairtrack.vehicle.application.RegisterVehicleCommand;
import com.repairtrack.vehicle.application.VehicleService;
import com.repairtrack.vehicle.domain.VehicleDetails;

@RestController
@RequestMapping("/api/v1/vehicles")
class VehicleController {

    private final VehicleService vehicleService;
    private final OwnershipService ownershipService;

    VehicleController(VehicleService vehicleService, OwnershipService ownershipService) {
        this.vehicleService = vehicleService;
        this.ownershipService = ownershipService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    VehicleResponse register(@AuthenticationPrincipal AuthenticatedUser actor,
                             @Valid @RequestBody RegisterVehicleRequest request) {
        var details = new VehicleDetails(request.licensePlate(), request.make(), request.model(),
                request.modelYear(), request.firstRegistrationDate());
        var command = new RegisterVehicleCommand(request.vin(), details, request.garageId(), request.ownedSince());
        return VehicleResponse.from(vehicleService.register(actor, command));
    }

    /** Vehicles the caller currently owns. */
    @GetMapping
    List<VehicleResponse> myVehicles(@AuthenticationPrincipal AuthenticatedUser actor) {
        return vehicleService.myVehicles(actor).stream().map(VehicleResponse::from).toList();
    }

    /** Exact lookup by exactly one of {@code vin} or {@code licensePlate}. */
    @GetMapping("/search")
    List<VehicleSearchResponse> search(@RequestParam(name = "vin", required = false) String vin,
                                       @RequestParam(name = "licensePlate", required = false) String licensePlate) {
        return vehicleService.search(vin, licensePlate).stream().map(VehicleSearchResponse::from).toList();
    }

    @GetMapping("/{vehicleId}")
    VehicleResponse get(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable("vehicleId") UUID vehicleId) {
        return VehicleResponse.from(vehicleService.get(actor, vehicleId));
    }

    @PutMapping("/{vehicleId}")
    VehicleResponse update(@AuthenticationPrincipal AuthenticatedUser actor,
                           @PathVariable("vehicleId") UUID vehicleId,
                           @Valid @RequestBody UpdateVehicleRequest request) {
        var details = new VehicleDetails(request.licensePlate(), request.make(), request.model(),
                request.modelYear(), request.firstRegistrationDate());
        return VehicleResponse.from(vehicleService.updateDetails(actor, vehicleId, details));
    }

    @PostMapping("/{vehicleId}/claim")
    @ResponseStatus(HttpStatus.CREATED)
    OwnershipResponse claim(@AuthenticationPrincipal AuthenticatedUser actor,
                            @PathVariable("vehicleId") UUID vehicleId,
                            @Valid @RequestBody ClaimVehicleRequest request) {
        return OwnershipResponse.from(ownershipService.claim(actor, vehicleId, request.vin(), request.ownedSince()));
    }

    @PostMapping("/{vehicleId}/ownership/end")
    OwnershipResponse endOwnership(@AuthenticationPrincipal AuthenticatedUser actor,
                                   @PathVariable("vehicleId") UUID vehicleId,
                                   @RequestBody(required = false) EndOwnershipRequest request) {
        return OwnershipResponse.from(
                ownershipService.endOwnership(actor, vehicleId, request == null ? null : request.endDate()));
    }
}
