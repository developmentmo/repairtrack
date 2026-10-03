package com.repairtrack.common.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory fixed-window counter per key (e.g. an IP address or an email address): at most {@code limit}
 * events per {@code window}. Thread-safe. Expired windows are swept regularly, so memory stays bounded by the
 * number of keys active within one window.
 * <p>
 * Per application instance: with several instances behind a load balancer the effective limit is multiplied
 * by the number of instances. A shared store (e.g. Redis) is the upgrade path when that matters.
 */
public final class FixedWindowRateLimiter {

    public record Decision(boolean allowed, Duration retryAfter) {

        static final Decision ALLOWED = new Decision(true, Duration.ZERO);
    }

    private record Window(long startMillis, int count) {
    }

    private static final int SWEEP_EVERY = 1_000;

    private final int limit;
    private final long windowMillis;
    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final AtomicLong calls = new AtomicLong();

    public FixedWindowRateLimiter(int limit, Duration window, Clock clock) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be at least 1");
        }
        if (window == null || window.isNegative() || window.isZero()) {
            throw new IllegalArgumentException("window must be positive");
        }
        this.limit = limit;
        this.windowMillis = window.toMillis();
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Counts one event for {@code key} and tells whether it is within the limit. */
    public Decision tryAcquire(String key) {
        long now = clock.millis();
        Window window = windows.compute(key, (k, current) -> current == null || expired(current, now)
                ? new Window(now, 1)
                : new Window(current.startMillis(), current.count() + 1));
        sweepOccasionally(now);
        if (window.count() <= limit) {
            return Decision.ALLOWED;
        }
        return new Decision(false, retryAfter(window, now));
    }

    /** Without counting: the remaining wait if {@code key} has used up its limit, empty otherwise. */
    public Optional<Duration> retryAfterIfLimited(String key) {
        long now = clock.millis();
        Window window = windows.get(key);
        if (window == null || expired(window, now) || window.count() < limit) {
            return Optional.empty();
        }
        return Optional.of(retryAfter(window, now));
    }

    public void reset(String key) {
        windows.remove(key);
    }

    int trackedKeys() {
        return windows.size();
    }

    private boolean expired(Window window, long now) {
        return now - window.startMillis() >= windowMillis;
    }

    private Duration retryAfter(Window window, long now) {
        return Duration.ofMillis(Math.max(0, window.startMillis() + windowMillis - now));
    }

    private void sweepOccasionally(long now) {
        if (calls.incrementAndGet() % SWEEP_EVERY == 0) {
            windows.values().removeIf(window -> expired(window, now));
        }
    }
}
