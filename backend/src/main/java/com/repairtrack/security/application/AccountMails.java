package com.repairtrack.security.application;

/**
 * Internal events of the security module: "send this email after the transaction commits". They carry the raw
 * token, so they never leave the module (not audited, not logged; {@code toString} hides the token).
 */
final class AccountMails {

    private AccountMails() {
    }

    record VerificationRequested(String email, String firstName, String token) {

        @Override
        public String toString() {
            return "VerificationRequested[***]";
        }
    }

    record PasswordResetRequested(String email, String firstName, String token) {

        @Override
        public String toString() {
            return "PasswordResetRequested[***]";
        }
    }
}
