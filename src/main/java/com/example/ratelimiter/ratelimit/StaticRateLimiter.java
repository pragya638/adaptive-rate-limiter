package com.example.ratelimiter.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;

import com.example.ratelimiter.ratelimit.store.CounterIncrement;
import com.example.ratelimiter.ratelimit.store.CounterStore;
import com.example.ratelimiter.ratelimit.store.InMemoryCounterStore;

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
    private final CounterStore counterStore;

    public StaticRateLimiter(int maxRequests, Duration window, Clock clock) {
        this(maxRequests, window, clock, new InMemoryCounterStore());
    }

    public StaticRateLimiter(int maxRequests, Duration window, Clock clock, CounterStore counterStore) {
        if (maxRequests < 1) {
            throw new IllegalArgumentException("maxRequests must be >= 1");
        }
        if (window == null || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("window must be positive");
        }
        if (clock == null) {
            throw new IllegalArgumentException("clock cannot be null");
        }
        this.maxRequests = maxRequests;
        this.windowMillis = window.toMillis();
        this.clock = clock;
        this.counterStore = Objects.requireNonNull(counterStore, "counterStore");
    }

    @Override
    public RateLimitDecision tryAcquire(String key) {
        long now = clock.millis();
        long windowStart = now - (now % windowMillis);

        CounterIncrement counter = counterStore.increment(key, windowStart, windowMillis);

        long count = counter.count();
        boolean allowed = count <= maxRequests;
        int remaining = count >= maxRequests ? 0 : (int) (maxRequests - count);
        long retryAfterSeconds = allowed ? 0L : secondsUntilReset(now, windowStart);
        return new RateLimitDecision(allowed, maxRequests, remaining, retryAfterSeconds);
    }

    @Override
    public void reset() {
        counterStore.reset();
    }

    private long secondsUntilReset(long now, long windowStart) {
        long millisLeft = windowStart + windowMillis - now;
        return Math.max(1L, (millisLeft + 999) / 1000);
    }
}
