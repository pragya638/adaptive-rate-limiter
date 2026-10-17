package com.example.ratelimiter.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Phase 1 algorithm: a thread-safe fixed-window counter.
 *
 * <p>Each client key gets a counter that is reset at every window boundary
 * ({@code now / window}). The counter never waits: it simply reports how many
 * seconds remain until the next reset.
 *
 * <p>This class contains no web/HTTP concerns, which keeps the algorithm
 * independently unit-testable and swappable in later phases.
 */
public class StaticRateLimiter implements RateLimiter {

    private final int maxRequests;
    private final long windowMillis;
    private final Clock clock;
    private final ConcurrentHashMap<String, WindowCounter> counters = new ConcurrentHashMap<>();

    public StaticRateLimiter(int maxRequests, Duration window, Clock clock) {
        if (maxRequests < 1) {
            throw new IllegalArgumentException("maxRequests must be >= 1");
        }
        if (window == null || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("window must be positive");
        }
        this.maxRequests = maxRequests;
        this.windowMillis = window.toMillis();
        this.clock = clock;
    }

    @Override
    public RateLimitDecision tryAcquire(String key) {
        long now = clock.millis();
        long windowStart = now - (now % windowMillis);

        WindowCounter counter = counters.compute(key, (k, current) -> {
            if (current == null || current.windowStart != windowStart) {
                return new WindowCounter(windowStart, 1);
            }
            return new WindowCounter(current.windowStart, current.count + 1);
        });

        boolean allowed = counter.count <= maxRequests;
        int remaining = Math.max(0, maxRequests - counter.count);
        long retryAfterSeconds = allowed ? 0L : secondsUntilReset(now, windowStart);
        return new RateLimitDecision(allowed, maxRequests, remaining, retryAfterSeconds);
    }

    @Override
    public void reset() {
        counters.clear();
    }

    private long secondsUntilReset(long now, long windowStart) {
        long millisLeft = windowStart + windowMillis - now;
        return Math.max(1L, (millisLeft + 999) / 1000);
    }

    private record WindowCounter(long windowStart, int count) {
    }
}
