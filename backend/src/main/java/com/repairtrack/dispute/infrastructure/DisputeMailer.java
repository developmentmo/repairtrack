package com.repairtrack.dispute.infrastructure;

/** Sends a plain-text email about a dispute. Failures are logged and swallowed (the change is committed). */
public interface DisputeMailer {

    void send(String to, String subject, String text);
}
