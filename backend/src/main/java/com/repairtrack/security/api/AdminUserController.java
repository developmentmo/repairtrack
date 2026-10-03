package com.repairtrack.security.api;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.repairtrack.security.AuthenticatedUser;
import com.repairtrack.security.Role;
import com.repairtrack.security.application.UserAdminService;
import com.repairtrack.security.application.UserNotFoundException;
import com.repairtrack.security.application.UserProfile;
import com.repairtrack.security.domain.UserStatus;

/** SYSTEM_ADMIN only (checked in {@link UserAdminService}). */
@RestController
@RequestMapping("/api/v1/admin/users")
class AdminUserController {

    private final UserAdminService adminService;

    AdminUserController(UserAdminService adminService) {
        this.adminService = adminService;
    }

    /** Exact (case-insensitive) email; no listing or partial search of users. */
    @GetMapping
    AdminUserResponse find(@AuthenticationPrincipal AuthenticatedUser actor, @RequestParam("email") String email) {
        return adminService.findByEmail(actor, email).map(AdminUserResponse::from)
                .orElseThrow(UserNotFoundException::new);
    }

    @PostMapping("/{userId}/block")
    AdminUserResponse block(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable("userId") UUID userId) {
        return AdminUserResponse.from(adminService.block(actor, userId));
    }

    @PostMapping("/{userId}/unblock")
    AdminUserResponse unblock(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable("userId") UUID userId) {
        return AdminUserResponse.from(adminService.unblock(actor, userId));
    }

    public record AdminUserResponse(UUID id, String email, String firstName, String lastName, UserStatus status,
                             Set<Role> roles, boolean emailVerified, Instant createdAt) {

        static AdminUserResponse from(UserProfile p) {
            return new AdminUserResponse(p.id(), p.email(), p.firstName(), p.lastName(), p.status(), p.roles(),
                    p.emailVerified(), p.createdAt());
        }
    }
}
