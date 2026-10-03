package com.repairtrack.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class FixedWindowRateLimiterTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-03T10:00:00Z"));
    private final FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(3, Duration.ofMinutes(1), clock);

    @Test
    void allowsUpToTheLimitPerWindowAndKey() {
        assertThat(limiter.tryAcquire("a").allowed()).isTrue();
        assertThat(limiter.tryAcquire("a").allowed()).isTrue();
        assertThat(limiter.tryAcquire("a").allowed()).isTrue();

        FixedWindowRateLimiter.Decision fourth = limiter.tryAcquire("a");

        assertThat(fourth.allowed()).isFalse();
        assertThat(fourth.retryAfter()).isEqualTo(Duration.ofMinutes(1));
        assertThat(limiter.tryAcquire("b").allowed()).isTrue();
    }

    @Test
    void aNewWindowStartsAfterTheOldOneExpires() {
        for (int i = 0; i < 4; i++) {
            limiter.tryAcquire("a");
        }
        clock.advance(Duration.ofSeconds(59));
        assertThat(limiter.tryAcquire("a").retryAfter()).isEqualTo(Duration.ofSeconds(1));

        clock.advance(Duration.ofSeconds(1));

        assertThat(limiter.tryAcquire("a").allowed()).isTrue();
    }

    @Test
    void checkingDoesNotCountAndResetClears() {
        limiter.tryAcquire("a");
        limiter.tryAcquire("a");
        assertThat(limiter.retryAfterIfLimited("a")).isEmpty();

        limiter.tryAcquire("a");
        assertThat(limiter.retryAfterIfLimited("a")).isPresent();
        assertThat(limiter.retryAfterIfLimited("a")).isPresent();

        limiter.reset("a");
        assertThat(limiter.retryAfterIfLimited("a")).isEmpty();
    }

    @Test
    void expiredWindowsAreSweptSoMemoryStaysBounded() {
        for (int i = 0; i < 999; i++) {
            limiter.tryAcquire("ip-" + i);
        }
        clock.advance(Duration.ofMinutes(2));

        limiter.tryAcquire("fresh"); // 1000th call triggers the sweep

        assertThat(limiter.trackedKeys()).isEqualTo(1);
    }

    private static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
