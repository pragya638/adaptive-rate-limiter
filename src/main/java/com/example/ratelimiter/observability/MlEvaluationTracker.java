package com.example.ratelimiter.observability;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Component;

/**
 * Read-only, in-memory record of the most recent ML detection attempts.
 *
 * <p>This component only records outcomes reported by
 * {@link com.example.ratelimiter.adaptive.HttpAnomalyAssessmentProvider}; it
 * never alters an assessment, a decision, or a model. Values are lost on
 * restart, and a never-attempted evaluation is reported as {@code null} instead
 * of being synthesised.
 */
@Component
public class MlEvaluationTracker {

    private final Clock clock;

    private volatile Instant lastAttemptAt;
    private volatile Instant lastSuccessAt;
    private volatile Instant lastFailureAt;
    private volatile String lastFailureDetail;

    public MlEvaluationTracker(Clock clock) {
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    public void recordSuccess() {
        Instant now = clock.instant();
        lastAttemptAt = now;
        lastSuccessAt = now;
    }

    public void recordFailure(String detail) {
        Instant now = clock.instant();
        lastAttemptAt = now;
        lastFailureAt = now;
        lastFailureDetail = detail;
    }

    public Instant getLastAttemptAt() {
        return lastAttemptAt;
    }

    public Instant getLastSuccessAt() {
        return lastSuccessAt;
    }

    public Instant getLastFailureAt() {
        return lastFailureAt;
    }

    public String getLastFailureDetail() {
        return lastFailureDetail;
    }
}
