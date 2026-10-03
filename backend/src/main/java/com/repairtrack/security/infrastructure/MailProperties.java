package com.repairtrack.security.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param from        sender of account emails, e.g. {@code RepairTrack <noreply@repairtrack.nl>}
 * @param linkBaseUrl base URL of the web app; links are {@code {linkBaseUrl}/verify-email?token=...} and
 *                    {@code /reset-password?token=...}
 */
@ConfigurationProperties(prefix = "repairtrack.mail")
public record MailProperties(String from, String linkBaseUrl) {

    public MailProperties {
        if (from == null || from.isBlank()) {
            throw new IllegalStateException("repairtrack.mail.from (MAIL_FROM) must be configured");
        }
        if (linkBaseUrl == null || linkBaseUrl.isBlank()) {
            throw new IllegalStateException("repairtrack.mail.link-base-url (PUBLIC_BASE_URL) must be configured");
        }
        linkBaseUrl = linkBaseUrl.replaceAll("/+$", "");
    }
}
