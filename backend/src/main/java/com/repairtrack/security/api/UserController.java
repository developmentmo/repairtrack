package com.repairtrack.security.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.repairtrack.security.AuthenticatedUser;
import com.repairtrack.security.application.AccountDeletionService;
import com.repairtrack.security.application.UserService;

@RestController
@RequestMapping("/api/v1/users")
class UserController {

    /** {@code toString} hides the password. */
    record DeleteAccountRequest(@NotBlank String password) {

        @Override
        public String toString() {
            return "DeleteAccountRequest[***]";
        }
    }

    private final UserService userService;
    private final AccountDeletionService accountDeletionService;

    UserController(UserService userService, AccountDeletionService accountDeletionService) {
        this.userService = userService;
        this.accountDeletionService = accountDeletionService;
    }

    @GetMapping("/me")
    UserResponse me(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        return UserResponse.from(userService.getProfile(currentUser.id()));
    }

    /**
     * Deletes the caller's account (password required again). The vehicle history stays with the vehicles; personal
     * details are removed. 204; afterwards every token of the account is invalid.
     */
    @PostMapping("/me/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@AuthenticationPrincipal AuthenticatedUser currentUser,
                @Valid @RequestBody DeleteAccountRequest request) {
        accountDeletionService.deleteOwnAccount(currentUser, request.password());
    }
}
