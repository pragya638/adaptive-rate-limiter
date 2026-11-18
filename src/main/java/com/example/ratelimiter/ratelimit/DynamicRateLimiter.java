package com.example.ratelimiter.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;

import com.example.ratelimiter.adaptive.store.AdaptiveLimitStore;
import com.example.ratelimiter.adaptive.store.InMemoryAdaptiveLimitStore;
import com.example.ratelimiter.ratelimit.store.CounterIncrement;
import com.example.ratelimiter.ratelimit.store.CounterStore;
import com.example.ratelimiter.ratelimit.store.InMemoryCounterStore;

/**
 * Phase 5 rate limiter with a thread-safe, dynamically adjustable request
 * limit.
 *
 * <p>The window accounting is the same fixed-window algorithm used by
 * {@link StaticRateLimiter}. The current maximum can be changed at runtime
 * through {@link #updateLimit(int)}; every update is clamped to the configured
 * minimum and maximum bounds so callers cannot move the limiter outside its
 * safety envelope.
 *
 * <p>Counters and the current limit are delegated to storage abstractions. The
 * default constructors keep the original in-process behaviour; the full
 * constructor is used to plug in Redis-backed implementations.
 */
public class DynamicRateLimiter implements RateLimiter {

    private final int minLimit;
    private final int maxLimit;
    private final long windowMillis;
    private final Clock clock;
    private final CounterStore counterStore;
    private final AdaptiveLimitStore limitStore;

    public DynamicRateLimiter(int initialMaxRequests, int minLimit, int maxLimit, Duration window, Clock clock) {
        this(minLimit, maxLimit, window, clock,
                new InMemoryCounterStore(),
                new InMemoryAdaptiveLimitStore(initialMaxRequests, minLimit, maxLimit));
    }

    public DynamicRateLimiter(int minLimit, int maxLimit, Duration window, Clock clock,
            CounterStore counterStore, AdaptiveLimitStore limitStore) {
        if (minLimit < 1) {
            throw new IllegalArgumentException("minLimit must be >= 1");
        }
        if (maxLimit < minLimit) {
            throw new IllegalArgumentException("maxLimit must be >= minLimit");
        }
        if (window == null || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("window must be positive");
        }
        if (clock == null) {
            throw new IllegalArgumentException("clock cannot be null");
        }
        this.minLimit = minLimit;
        this.maxLimit = maxLimit;
        this.windowMillis = window.toMillis();
        this.clock = clock;
        this.counterStore = Objects.requireNonNull(counterStore, "counterStore");
        this.limitStore = Objects.requireNonNull(limitStore, "limitStore");
    }

    @Override
    public RateLimitDecision tryAcquire(String key) {
        int limit = limitStore.getCurrentLimit();
        long now = clock.millis();
        long windowStart = now - (now % windowMillis);

        CounterIncrement counter = counterStore.increment(key, windowStart, windowMillis);

        long count = counter.count();
        boolean allowed = count <= limit;
        long remainingLong = (long) limit - count;
        int remaining = remainingLong <= 0 ? 0 : (int) Math.min(limit, remainingLong);
        long retryAfterSeconds = allowed ? 0L : secondsUntilReset(now, windowStart);
        return new RateLimitDecision(allowed, limit, remaining, retryAfterSeconds);
    }

    @Override
    public void reset() {
        counterStore.reset();
    }

    /**
     * Updates the current limit, clamping it to the configured bounds.
     *
     * @return the limit actually in effect after clamping
     */
    public int updateLimit(int proposedLimit) {
        return limitStore.updateLimit(clamp(proposedLimit));
    }

    public int getCurrentLimit() {
        return limitStore.getCurrentLimit();
    }

    public int getMinLimit() {
        return minLimit;
    }

    public int getMaxLimit() {
        return maxLimit;
    }

    private int clamp(int value) {
        if (value < minLimit) {
            return minLimit;
        }
        if (value > maxLimit) {
            return maxLimit;
        }
        return value;
    }

    private long secondsUntilReset(long now, long windowStart) {
        long millisLeft = windowStart + windowMillis - now;
        return Math.max(1L, (millisLeft + 999) / 1000);
    }
}
