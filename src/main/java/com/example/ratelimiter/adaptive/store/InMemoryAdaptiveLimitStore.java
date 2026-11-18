package com.example.ratelimiter.adaptive.store;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Default process-local adaptive limit storage.
 */
public class InMemoryAdaptiveLimitStore implements AdaptiveLimitStore {

    private final int minLimit;
    private final int maxLimit;
    private final AtomicInteger currentLimit;

    public InMemoryAdaptiveLimitStore(int initialLimit, int minLimit, int maxLimit) {
        this.minLimit = minLimit;
        this.maxLimit = maxLimit;
        validateBounds(minLimit, maxLimit);
        this.currentLimit = new AtomicInteger(clamp(initialLimit));
    }

    @Override
    public int getCurrentLimit() {
        return currentLimit.get();
    }

    @Override
    public int updateLimit(int proposedLimit) {
        int clamped = clamp(proposedLimit);
        currentLimit.set(clamped);
        return currentLimit.get();
    }

    private int clamp(int value) {
        return Math.max(minLimit, Math.min(maxLimit, value));
    }

    private static void validateBounds(int minLimit, int maxLimit) {
        if (minLimit < 1 || maxLimit < minLimit) {
            throw new IllegalArgumentException("Invalid adaptive limit bounds");
        }
    }
}
