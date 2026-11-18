package com.example.ratelimiter.adaptive.store;

/**
 * Storage abstraction for the current adaptive rate limit.
 *
 * <p>The limit is global to the configured rate limiter, not per client. A
 * shared implementation makes limit updates visible to every application
 * instance.
 */
public interface AdaptiveLimitStore {

    /** Return the current limit, clamped to the configured bounds. */
    int getCurrentLimit();

    /**
     * Persist a proposed limit after clamping it to the configured bounds.
     *
     * @return the effective limit after the update
     */
    int updateLimit(int proposedLimit);
}
