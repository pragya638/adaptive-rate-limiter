package com.example.ratelimiter.adaptive.lock;

/**
 * Prevents more than one application instance from applying an adaptive limit
 * update for the same evaluation cycle.
 */
public interface AdaptiveEvaluationLock {

    /** Try to become the leader for this evaluation cycle. */
    boolean tryAcquire();

    /** Release the lock if this instance still owns it. */
    void release();
}
