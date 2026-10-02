package com.repairtrack.common.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class BusinessCalendarTest {

    private static final ZoneId AMSTERDAM = ZoneId.of("Europe/Amsterdam");

    @Test
    void todayIsTheAmsterdamDateNotTheUtcDate() {
        // 22:30 UTC on 25 Sept = 00:30 CEST on 26 Sept
        Clock clock = Clock.fixed(Instant.parse("2026-09-25T22:30:00Z"), ZoneOffset.UTC);

        BusinessCalendar calendar = new BusinessCalendar(clock, AMSTERDAM);

        assertThat(calendar.today()).isEqualTo(LocalDate.of(2026, 9, 26));
        assertThat(calendar.now()).isEqualTo(Instant.parse("2026-09-25T22:30:00Z"));
    }
}
