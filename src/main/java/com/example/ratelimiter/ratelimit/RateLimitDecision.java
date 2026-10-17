package com.example.ratelimiter.ratelimit;

/**
 * Immutable outcome of a rate-limit check.
 *
 * @param allowed           true when the request may be served
 * @param limit             configured maximum requests per window
 * @param remaining         requests left in the current window (never negative)
 * @param retryAfterSeconds seconds until the window resets (0 when allowed)
 */
public record RateLimitDecision(boolean allowed, int limit, int remaining, long retryAfterSeconds) {
}
