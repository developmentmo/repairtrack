package com.repairtrack.security.api;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.repairtrack.security.AuthenticatedUser;
import com.repairtrack.security.application.UserService;

@RestController
@RequestMapping("/api/v1/users")
class UserController {

    private final UserService userService;

    UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    UserResponse me(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        return UserResponse.from(userService.getProfile(currentUser.id()));
    }
}
