package com.repairtrack.common.time;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * "Now" and "today" for business rules.
 * <p>
 * Instants are always UTC. Calendar dates entered by users (repair date, ownership start) are
 * local dates in the business time zone (Europe/Amsterdam), so "not in the future" must be judged
 * in that zone: at 00:30 in Amsterdam it is still yesterday in UTC.
 * Do not use Bean Validation {@code @PastOrPresent} on such dates; it uses the JVM default zone.
 */
@Component
public class BusinessCalendar {

    private final Clock clock;
    private final ZoneId zone;

    public BusinessCalendar(Clock clock,
                            @Value("${repairtrack.business-time-zone:Europe/Amsterdam}") ZoneId zone) {
        this.clock = clock;
        this.zone = zone;
    }

    public Instant now() {
        return Instant.now(clock);
    }

    public LocalDate today() {
        return LocalDate.now(clock.withZone(zone));
    }

    public ZoneId zone() {
        return zone;
    }
}
