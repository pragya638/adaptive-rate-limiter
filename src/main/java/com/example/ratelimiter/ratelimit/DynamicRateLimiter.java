package com.example.ratelimiter.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Phase 5 rate limiter with a thread-safe, dynamically adjustable request
 * limit.
 *
 * <p>The window accounting is the same fixed-window algorithm used by
 * {@link StaticRateLimiter}. The current maximum can be changed at runtime
 * through {@link #updateLimit(int)}; every update is clamped to the configured
 * minimum and maximum bounds so callers cannot move the limiter outside its
 * safety envelope.
 */
public class DynamicRateLimiter implements RateLimiter {

    private final AtomicInteger maxRequests;
    private final int minLimit;
    private final int maxLimit;
    private final long windowMillis;
    private final Clock clock;
    private final ConcurrentHashMap<String, WindowCounter> counters = new ConcurrentHashMap<>();

    public DynamicRateLimiter(int initialMaxRequests, int minLimit, int maxLimit, Duration window, Clock clock) {
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
        this.maxRequests = new AtomicInteger(clamp(initialMaxRequests));
    }

    @Override
    public RateLimitDecision tryAcquire(String key) {
        int limit = maxRequests.get();
        long now = clock.millis();
        long windowStart = now - (now % windowMillis);

        WindowCounter counter = counters.compute(key, (k, current) -> {
            if (current == null || current.windowStart != windowStart) {
                return new WindowCounter(windowStart, 1);
            }
            return new WindowCounter(current.windowStart, current.count + 1);
        });

        boolean allowed = counter.count <= limit;
        int remaining = Math.max(0, limit - counter.count);
        long retryAfterSeconds = allowed ? 0L : secondsUntilReset(now, windowStart);
        return new RateLimitDecision(allowed, limit, remaining, retryAfterSeconds);
    }

    @Override
    public void reset() {
        counters.clear();
    }

    /**
     * Updates the current limit, clamping it to the configured bounds.
     *
     * @return the limit actually in effect after clamping
     */
    public int updateLimit(int proposedLimit) {
        return maxRequests.updateAndGet(previous -> clamp(proposedLimit));
    }

    public int getCurrentLimit() {
        return maxRequests.get();
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

    private record WindowCounter(long windowStart, int count) {
    }
}
