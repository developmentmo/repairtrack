package com.repairtrack;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import com.repairtrack.security.application.AccountMailer;

/**
 * Test replacement for the SMTP mailer: keeps account emails in memory so tests can follow the links. Test code
 * only; for {@link TestRepairTrackApplication} it also prints the token, since no real mail is sent there.
 */
public class RecordingAccountMailer implements AccountMailer {

    public enum Kind { VERIFICATION, PASSWORD_RESET }

    public record Mail(String to, Kind kind, String token) {
    }

    private final List<Mail> mails = new CopyOnWriteArrayList<>();

    @Override
    public void sendEmailVerification(String to, String firstName, String token) {
        record(new Mail(to, Kind.VERIFICATION, token));
    }

    @Override
    public void sendPasswordReset(String to, String firstName, String token) {
        record(new Mail(to, Kind.PASSWORD_RESET, token));
    }

    public Optional<String> lastToken(String to, Kind kind) {
        return mails.stream().filter(m -> m.to().equals(to) && m.kind() == kind).reduce((a, b) -> b).map(Mail::token);
    }

    public long count(String to, Kind kind) {
        return mails.stream().filter(m -> m.to().equals(to) && m.kind() == kind).count();
    }

    private void record(Mail mail) {
        mails.add(mail);
        System.out.println("[test mailer] " + mail.kind() + " for " + mail.to() + ": token=" + mail.token());
    }
}
