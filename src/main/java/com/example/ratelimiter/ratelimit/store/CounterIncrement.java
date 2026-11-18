package com.example.ratelimiter.ratelimit.store;

/**
 * Result of incrementing one client's fixed-window counter.
 *
 * @param count    current count in the window
 * @param degraded whether the result came from a fallback path because the
 *                 configured shared store was unavailable
 */
public record CounterIncrement(long count, boolean degraded) {
}
