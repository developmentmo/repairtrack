package com.repairtrack.dispute.application;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.repairtrack.common.time.BusinessCalendar;
import com.repairtrack.dispute.domain.DisputeStatus;
import com.repairtrack.dispute.domain.OwnershipDispute;
import com.repairtrack.dispute.infrastructure.DisputeMailer;
import com.repairtrack.security.UserDirectory;
import com.repairtrack.security.UserSummary;
import com.repairtrack.vehicle.VehicleSummary;

/**
 * Dutch emails to the parties. Never tells one party who the other is or what they wrote. Sent only after the
 * transaction committed (a rolled-back dispute never mails anyone).
 */
@Component
class DisputeMails {

    /** Internal: "send this after commit". */
    record MailRequested(String to, String subject, String text) {

        @Override
        public String toString() {
            return "MailRequested[" + subject + "]";
        }
    }

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d-M-yyyy");

    private final UserDirectory users;
    private final ApplicationEventPublisher events;
    private final DisputeMailer mailer;
    private final BusinessCalendar calendar;
    private final String appUrl;

    DisputeMails(UserDirectory users, ApplicationEventPublisher events, DisputeMailer mailer,
                 BusinessCalendar calendar, @Value("${repairtrack.mail.link-base-url}") String linkBaseUrl) {
        this.users = users;
        this.events = events;
        this.mailer = mailer;
        this.calendar = calendar;
        this.appUrl = linkBaseUrl.replaceAll("/+$", "") + "/disputes";
    }

    void opened(OwnershipDispute dispute, VehicleSummary vehicle) {
        Map<UUID, UserSummary> parties = users.findByIds(List.of(dispute.getClaimantId(), dispute.getOwnerId()));
        String car = describe(vehicle);
        send(parties.get(dispute.getOwnerId()), "Je eigendom van " + car + " wordt betwist", """
                Iemand heeft bij RepairTrack aangegeven de rechtmatige eigenaar te zijn van %s, dat nu op jouw naam
                staat in RepairTrack.

                Geef vóór %s je reactie in de RepairTrack-app en voeg bewijs toe dat je de eigenaar bent, zoals een
                koopcontract of overschrijvingsbewijs. Scherm daarop altijd de tenaamstellingscode af.

                %s

                Zolang het geschil loopt, kun je geen nieuwe deellinks maken. Reageer je niet op tijd, dan beoordeelt
                RepairTrack het geschil met de informatie die er dan is.
                """.formatted(car, date(dispute.getResponseDeadline()), appUrl));
        send(parties.get(dispute.getClaimantId()), "We hebben je geschil over " + car + " ontvangen", """
                Je geschil over het eigendom van %s is ontvangen. De huidige eigenaar kan tot %s reageren; daarna
                beoordeelt RepairTrack het geschil. Je krijgt een e-mail met de uitkomst.

                Je kunt in de app nog extra bewijs toevoegen:
                %s
                """.formatted(car, date(dispute.getResponseDeadline()), appUrl));
    }

    void decided(OwnershipDispute dispute, VehicleSummary vehicle) {
        Map<UUID, UserSummary> parties = users.findByIds(List.of(dispute.getClaimantId(), dispute.getOwnerId()));
        String car = describe(vehicle);
        boolean upheld = dispute.getStatus() == DisputeStatus.UPHELD;
        String note = "Toelichting van RepairTrack:\n" + dispute.getDecisionNote();
        if (upheld) {
            send(parties.get(dispute.getClaimantId()), "Je geschil over " + car + " is toegekend", """
                    RepairTrack heeft je geschil toegekend. Je bent nu de eigenaar van %s in RepairTrack (vanaf %s).

                    %s

                    %s
                    """.formatted(car, dispute.getNewOwnerSince().format(DATE), note, appUrl));
            send(parties.get(dispute.getOwnerId()), "Je eigendom van " + car + " is ingetrokken", """
                    RepairTrack heeft het geschil over %s beoordeeld en vastgesteld dat jij niet de rechtmatige
                    eigenaar bent. Je eigendom in RepairTrack is ingetrokken; je deellinks werken niet meer.

                    %s

                    Ben je het hier niet mee eens? Antwoord dan op deze e-mail.
                    """.formatted(car, note));
        } else {
            send(parties.get(dispute.getClaimantId()), "Je geschil over " + car + " is afgewezen", """
                    RepairTrack heeft je geschil over %s beoordeeld en afgewezen. Het eigendom blijft ongewijzigd.

                    %s
                    """.formatted(car, note));
            send(parties.get(dispute.getOwnerId()), "Het geschil over " + car + " is afgewezen", """
                    Het geschil over het eigendom van %s is afgewezen. Je blijft de eigenaar in RepairTrack en kunt
                    weer deellinks maken.

                    %s
                    """.formatted(car, note));
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(MailRequested mail) {
        mailer.send(mail.to(), mail.subject(), mail.text());
    }

    private void send(UserSummary to, String subject, String body) {
        if (to == null || !to.active()) {
            return; // deleted or blocked account: nobody to tell
        }
        String text = "Hallo " + to.firstName() + ",\n\n" + body + "\nRepairTrack\n";
        events.publishEvent(new MailRequested(to.email(), subject, text));
    }

    private String date(Instant instant) {
        return instant.atZone(calendar.zone()).toLocalDate().format(DATE);
    }

    private static String describe(VehicleSummary vehicle) {
        if (vehicle == null) {
            return "je voertuig";
        }
        String name = vehicle.make() + " " + vehicle.model();
        return vehicle.licensePlate() == null ? name : name + " (" + vehicle.licensePlate() + ")";
    }
}
