package com.example.ratelimiter.ratelimit;

/**
 * Decides whether a request identified by {@code key} may proceed.
 *
 * <p>Kept deliberately small so that later phases (EWMA trend analysis,
 * anomaly detection) can provide alternative implementations without
 * touching the web layer.
 */
public interface RateLimiter {

    /**
     * Attempts to consume one unit of quota for the given client key.
     *
     * @param key stable identifier for the caller (client id or IP address)
     * @return the outcome of the decision, never {@code null}
     */
    RateLimitDecision tryAcquire(String key);

    /** Clears all accumulated state. Intended for tests and admin use. */
    void reset();
}
