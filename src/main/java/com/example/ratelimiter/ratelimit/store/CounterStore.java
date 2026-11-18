package com.example.ratelimiter.ratelimit.store;

/**
 * Storage abstraction for fixed-window rate-limit counters.
 *
 * <p>Implementations must increment the counter for the supplied window
 * atomically. The limiter remains responsible for comparing the returned count
 * with its current limit and building the HTTP decision.
 */
public interface CounterStore {

    /**
     * Increment the counter for one client in one fixed window.
     *
     * @param clientKey             client identity (header value or IP)
     * @param windowStartEpochMillis epoch-aligned start of the window
     * @param windowMillis           window length in milliseconds
     * @return the count after this increment and whether a fallback path was used
     */
    CounterIncrement increment(String clientKey, long windowStartEpochMillis, long windowMillis);

    /** Clear stored counters. Intended for tests and administrative resets. */
    void reset();
}
