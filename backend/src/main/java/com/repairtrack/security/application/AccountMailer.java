package com.repairtrack.security.application;

/**
 * Sends account emails. The token is put into a link to the web app. Implementations must never log the token.
 */
public interface AccountMailer {

    void sendEmailVerification(String to, String firstName, String token);

    void sendPasswordReset(String to, String firstName, String token);
}
