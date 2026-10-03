package com.repairtrack.security.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.repairtrack.security.application.AccountService;
import com.repairtrack.security.application.AuthService;
import com.repairtrack.security.application.RegisterUserCommand;

@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

    private final AuthService authService;
    private final AccountService accountService;

    AuthController(AuthService authService, AccountService accountService) {
        this.authService = authService;
        this.accountService = accountService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    UserResponse register(@Valid @RequestBody RegisterRequest request) {
        var command = new RegisterUserCommand(request.email(), request.password(),
                request.firstName(), request.lastName());
        return UserResponse.from(authService.register(command));
    }

    @PostMapping("/login")
    TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return TokenResponse.from(authService.login(request.email(), request.password()));
    }

    @PostMapping("/refresh")
    TokenResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return TokenResponse.from(authService.refresh(request.refreshToken()));
    }

    /** Public on purpose: possession of the refresh token is the authorization to end its session. */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request.refreshToken());
    }

    // ---------- email verification and password reset (public, rate limited) ----------

    /** Always 202: never reveals whether the address has an (unverified) account. */
    @PostMapping("/resend-verification")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void resendVerification(@Valid @RequestBody EmailRequest request) {
        accountService.resendVerification(request.email());
    }

    @PostMapping("/verify-email")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void verifyEmail(@Valid @RequestBody TokenRequest request) {
        accountService.verifyEmail(request.token());
    }

    /** Always 202: never reveals whether the address has an account. */
    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void forgotPassword(@Valid @RequestBody EmailRequest request) {
        accountService.requestPasswordReset(request.email());
    }

    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        accountService.resetPassword(request.token(), request.newPassword());
    }

    public record EmailRequest(@NotBlank @Email @Size(max = 254) String email) {
    }

    public record TokenRequest(@NotBlank @Size(max = 200) String token) {

        @Override
        public String toString() {
            return "TokenRequest[***]";
        }
    }

    public record ResetPasswordRequest(@NotBlank @Size(max = 200) String token, @NotBlank @Size(max = 200) String newPassword) {

        @Override
        public String toString() {
            return "ResetPasswordRequest[***]";
        }
    }
}
