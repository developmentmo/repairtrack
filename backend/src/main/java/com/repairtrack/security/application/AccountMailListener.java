package com.repairtrack.security.application;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends account emails only after the transaction committed: a rolled-back registration never gets a mail, and a
 * slow or failing mail server never rolls back an account change.
 */
@Component
class AccountMailListener {

    private final AccountMailer mailer;

    AccountMailListener(AccountMailer mailer) {
        this.mailer = mailer;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(AccountMails.VerificationRequested mail) {
        mailer.sendEmailVerification(mail.email(), mail.firstName(), mail.token());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(AccountMails.PasswordResetRequested mail) {
        mailer.sendPasswordReset(mail.email(), mail.firstName(), mail.token());
    }
}
