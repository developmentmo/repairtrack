package com.repairtrack.security.infrastructure;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import com.repairtrack.security.application.AccountMailer;

/**
 * Sends account emails as plain text over SMTP. A failure is logged (without the link) and swallowed: the
 * account change is already committed and the user can ask for a new email.
 */
@Component
@EnableConfigurationProperties(MailProperties.class)
class SmtpAccountMailer implements AccountMailer {

    private static final Logger log = LoggerFactory.getLogger(SmtpAccountMailer.class);

    private final JavaMailSender mailSender;
    private final MailProperties properties;

    SmtpAccountMailer(JavaMailSender mailSender, MailProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public void sendEmailVerification(String to, String firstName, String token) {
        send(to, "Bevestig je e-mailadres voor RepairTrack", """
                Hallo %s,

                Bevestig je e-mailadres via deze link. De link is 24 uur geldig.

                %s

                Daarna kun je inloggen in de RepairTrack-app.

                Heb je geen account aangemaakt? Dan kun je deze e-mail negeren.

                RepairTrack
                """.formatted(firstName, link("/verify-email", token)));
    }

    @Override
    public void sendPasswordReset(String to, String firstName, String token) {
        send(to, "Je wachtwoord voor RepairTrack herstellen", """
                Hallo %s,

                Via deze link kies je een nieuw wachtwoord. De link is 1 uur geldig en werkt maar één keer.

                %s

                Na het herstellen word je op al je apparaten uitgelogd.

                Heb je dit niet aangevraagd? Negeer deze e-mail; je wachtwoord blijft dan ongewijzigd.

                RepairTrack
                """.formatted(firstName, link("/reset-password", token)));
    }

    private String link(String path, String token) {
        return properties.linkBaseUrl() + path + "?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
    }

    private void send(String to, String subject, String text) {
        var message = new SimpleMailMessage();
        message.setFrom(properties.from());
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        try {
            mailSender.send(message);
        } catch (MailException e) {
            log.warn("Could not send account email '{}': {}", subject, e.getMessage());
        }
    }
}
