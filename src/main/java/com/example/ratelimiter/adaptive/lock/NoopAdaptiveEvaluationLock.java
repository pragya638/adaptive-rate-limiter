package com.example.ratelimiter.adaptive.lock;

/**
 * Process-local lock used by the default in-memory deployment. The scheduler's
 * own atomic guard already prevents overlapping evaluations in one JVM.
 */
public class NoopAdaptiveEvaluationLock implements AdaptiveEvaluationLock {

    @Override
    public boolean tryAcquire() {
        return true;
    }

    @Override
    public void release() {
        // Nothing to release.
    }
}
