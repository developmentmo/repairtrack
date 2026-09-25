package com.repairtrack.common.time;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Single UTC time source for the application.
 * <p>
 * Inject {@link Clock} instead of calling {@code Instant.now()} / {@code LocalDate.now()} directly,
 * so time-dependent rules (e.g. "event date must not be in the future") are deterministic in tests.
 */
@Configuration(proxyBeanMethods = false)
public class TimeConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
