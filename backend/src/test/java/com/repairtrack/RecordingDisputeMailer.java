package com.repairtrack;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.repairtrack.dispute.infrastructure.DisputeMailer;

/** Test replacement for the SMTP dispute mailer: keeps the mails in memory. */
public class RecordingDisputeMailer implements DisputeMailer {

    public record Mail(String to, String subject, String text) {
    }

    private final List<Mail> mails = new CopyOnWriteArrayList<>();

    @Override
    public void send(String to, String subject, String text) {
        mails.add(new Mail(to, subject, text));
    }

    public List<Mail> to(String address) {
        return mails.stream().filter(m -> m.to().equals(address)).toList();
    }
}
