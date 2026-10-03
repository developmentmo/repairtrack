package com.repairtrack.dispute.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
@EnableConfigurationProperties(DisputeProperties.class)
class SmtpDisputeMailer implements DisputeMailer {

    private static final Logger log = LoggerFactory.getLogger(SmtpDisputeMailer.class);

    private final JavaMailSender mailSender;
    private final String from;

    SmtpDisputeMailer(JavaMailSender mailSender, @Value("${repairtrack.mail.from}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void send(String to, String subject, String text) {
        var message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        try {
            mailSender.send(message);
        } catch (MailException e) {
            log.warn("Could not send dispute email '{}': {}", subject, e.getMessage());
        }
    }
}
