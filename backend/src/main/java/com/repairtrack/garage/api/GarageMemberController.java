package com.repairtrack.garage.api;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.repairtrack.garage.application.GarageMemberService;
import com.repairtrack.security.AuthenticatedUser;

@RestController
@RequestMapping("/api/v1/garages/{garageId}/users")
class GarageMemberController {

    private final GarageMemberService memberService;

    GarageMemberController(GarageMemberService memberService) {
        this.memberService = memberService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    GarageMemberResponse add(@AuthenticationPrincipal AuthenticatedUser actor,
                             @PathVariable("garageId") UUID garageId,
                             @Valid @RequestBody AddGarageUserRequest request) {
        return GarageMemberResponse.from(memberService.addMember(actor, garageId, request.email(), request.role()));
    }

    @GetMapping
    List<GarageMemberResponse> list(@AuthenticationPrincipal AuthenticatedUser actor,
                                    @PathVariable("garageId") UUID garageId) {
        return memberService.listMembers(actor, garageId).stream().map(GarageMemberResponse::from).toList();
    }

    /**
     * Ends the membership (it is kept as history, not deleted). Garage admins can remove anyone
     * except the last admin; members can remove themselves.
     */
    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remove(@AuthenticationPrincipal AuthenticatedUser actor,
                @PathVariable("garageId") UUID garageId,
                @PathVariable("userId") UUID userId) {
        memberService.removeMember(actor, garageId, userId);
    }
}
